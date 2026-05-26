@file:OptIn(ExperimentalTime::class)

package io.github.hazemafaneh.liveactivities

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import io.github.hazemafaneh.liveactivities.internal.ActivityCodec
import io.github.hazemafaneh.liveactivities.internal.ActivityStore
import io.github.hazemafaneh.liveactivities.internal.AndroidNotifications
import io.github.hazemafaneh.liveactivities.internal.PersistedActivity
import io.github.hazemafaneh.liveactivities.internal.logPostResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * Android implementation of [LiveActivityManager], backed by `Notification.ProgressStyle`
 * (API 36+) with an ongoing `NotificationCompat` fallback on older versions.
 *
 * Call [init] from `Application.onCreate()` before any other member. Register a
 * [LiveActivityRenderer] per content-state type with [registerRenderer] to control the UI;
 * without one, a generic fallback renderer is used. Tracked activities are persisted to a
 * DataStore cache and restored after process death, provided their types are `@Serializable`.
 */
public actual object LiveActivityManager {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notificationIds: AtomicInteger = AtomicInteger(STARTING_NOTIFICATION_ID)
    private val renderers: MutableMap<KClass<*>, LiveActivityRenderer<*>> = ConcurrentHashMap()
    private val attributedRenderers: MutableMap<KClass<*>, AttributedLiveActivityRenderer<*, *>> =
        ConcurrentHashMap()
    private val tracked: MutableMap<String, TrackedActivity> = ConcurrentHashMap()
    private val restoreSignal: CompletableDeferred<Unit> = CompletableDeferred()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var store: ActivityStore? = null

    private val _activities: MutableStateFlow<List<LiveActivity<*, *>>> =
        MutableStateFlow(emptyList())
    public actual val activities: StateFlow<List<LiveActivity<*, *>>> = _activities.asStateFlow()

    private val _areActivitiesEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    public actual val areActivitiesEnabled: StateFlow<Boolean> =
        _areActivitiesEnabled.asStateFlow()

    /**
     * Initializes the manager with an application [Context].
     *
     * Call this once from `Application.onCreate()`. Android-only — there is no iOS equivalent.
     */
    public fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        store = ActivityStore(app)
        refreshAuthorization()
        scope.launch { restore() }
    }

    /**
     * Registers the [renderer] used to draw notifications for content states of type [stateType].
     *
     * Call this before [start]ing an activity whose state is of that type.
     */
    public fun <S : LiveActivityContentState> registerRenderer(
        stateType: KClass<S>,
        renderer: LiveActivityRenderer<S>,
    ) {
        renderers[stateType] = renderer
    }

    /** Reified convenience for [registerRenderer]. */
    public inline fun <reified S : LiveActivityContentState> registerRenderer(
        renderer: LiveActivityRenderer<S>,
    ) {
        registerRenderer(S::class, renderer)
    }

    /**
     * Registers the attributed [renderer] used to draw notifications for content states of type
     * [stateType]. Takes precedence over any plain [LiveActivityRenderer] registered for the same
     * type.
     *
     * Use this overload when the rendered content depends on attribute fields that aren't part
     * of the per-update state (e.g. vendor name captured at start time).
     */
    public fun <A : LiveActivityAttributes, S : LiveActivityContentState> registerAttributedRenderer(
        stateType: KClass<S>,
        renderer: AttributedLiveActivityRenderer<A, S>,
    ) {
        attributedRenderers[stateType] = renderer
    }


    /**
     * Starts or stops the bundled [LiveActivityForegroundService], which keeps the process alive
     * for long-running activities. Must be called while the app is in the foreground.
     */
    public fun setForegroundServiceEnabled(enabled: Boolean) {
        val context = appContext ?: return
        if (enabled) {
            LiveActivityForegroundService.start(context)
        } else {
            LiveActivityForegroundService.stop(context)
        }
    }

    @Suppress("UNCHECKED_CAST")
    public actual suspend fun <A : LiveActivityAttributes, S : LiveActivityContentState> start(
        attributes: A,
        initialState: S,
        config: LiveActivityConfig,
    ): Result<LiveActivity<A, S>> {
        val context = appContext ?: return Result.failure(notInitialized())
        refreshAuthorization()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Result.failure(LiveActivityException.NotAuthorized())
        }

        val id = "la_" + UUID.randomUUID()
        val stateFlow = MutableStateFlow(initialState)
        val statusFlow = MutableStateFlow(LiveActivityStatus.Active)
        val pushTokenFlow = MutableStateFlow<String?>(null)
        val handle = LiveActivity(
            id = id,
            attributes = attributes,
            state = stateFlow.asStateFlow(),
            pushToken = pushTokenFlow.asStateFlow(),
            status = statusFlow.asStateFlow(),
        )
        val entry = TrackedActivity(
            handle = handle,
            notificationId = notificationIds.getAndIncrement(),
            state = stateFlow as MutableStateFlow<LiveActivityContentState>,
            status = statusFlow,
            config = config,
        )

        return try {
            AndroidNotifications.ensureChannel(context, config)
            postNotification(context, entry)
            tracked[id] = entry
            publishActivities()
            persistSnapshot()
            Result.success(handle)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    public actual suspend fun <S : LiveActivityContentState> update(
        activityId: String,
        newState: S,
    ): Result<Unit> {
        val context = appContext ?: return Result.failure(notInitialized())
        val entry = tracked[activityId]
            ?: return Result.failure(LiveActivityException.ActivityNotFound(activityId))
        return try {
            entry.state.value = newState
            entry.status.value = LiveActivityStatus.Active
            postNotification(context, entry)
            publishActivities()
            persistSnapshot()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    public actual suspend fun end(
        activityId: String,
        dismissalPolicy: DismissalPolicy,
    ): Result<Unit> {
        val context = appContext ?: return Result.failure(notInitialized())
        val entry = tracked[activityId]
            ?: return Result.failure(LiveActivityException.ActivityNotFound(activityId))
        return try {
            applyDismissal(context, entry, dismissalPolicy)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    public actual suspend fun endAll(dismissalPolicy: DismissalPolicy): Result<Unit> {
        val context = appContext ?: return Result.failure(notInitialized())
        return try {
            tracked.values.toList().forEach { applyDismissal(context, it, dismissalPolicy) }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** Applies a server-pushed content-state update. Invoked by [LiveActivityPushHandler]. */
    internal suspend fun applyRemoteUpdate(activityId: String, stateJson: String): Result<Unit> {
        val context = appContext ?: return Result.failure(notInitialized())
        restoreSignal.await()
        val entry = tracked[activityId]
            ?: return Result.failure(LiveActivityException.ActivityNotFound(activityId))
        return try {
            val className = entry.state.value::class.java.name
            entry.state.value = ActivityCodec.decodeState(className, stateJson)
            entry.status.value = LiveActivityStatus.Active
            postNotification(context, entry)
            publishActivities()
            persistSnapshot()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(LiveActivityException.SerializationFailed("Failed to apply remote update.", t))
        }
    }

    private fun postNotification(context: Context, entry: TrackedActivity) {
        val state = entry.state.value
        val content = renderContent(entry.handle.attributes, state)
        val built = AndroidNotifications.build(
            context = context,
            config = entry.config,
            content = content,
            activityId = entry.handle.id,
            notificationId = entry.notificationId,
        ) ?: return
        NotificationManagerCompat.from(context).notify(entry.notificationId, built.notification)
        logPostResult(entry.handle.id, built.promotionRequested)
    }

    /**
     * Called by [LiveActivityDismissReceiver] when the user swipes the notification away.
     *
     * Flips the activity to [LiveActivityStatus.Dismissed], emits on [activities], and removes
     * it from the tracking map. Deliberately does not re-post — that decision is the consumer's.
     */
    internal fun handleSystemDismissal(activityId: String) {
        val entry = tracked.remove(activityId) ?: return
        entry.status.value = LiveActivityStatus.Dismissed
        publishActivities()
        persistSnapshot()
    }

    private fun applyDismissal(context: Context, entry: TrackedActivity, policy: DismissalPolicy) {
        when (policy) {
            DismissalPolicy.Immediate -> dismiss(context, entry)
            is DismissalPolicy.After -> {
                entry.status.value = LiveActivityStatus.Ended
                publishActivities()
                persistSnapshot()
                scope.launch {
                    delay(policy.duration)
                    dismiss(context, entry)
                }
            }
            is DismissalPolicy.At -> {
                entry.status.value = LiveActivityStatus.Ended
                publishActivities()
                persistSnapshot()
                val remaining = policy.instant - Clock.System.now()
                scope.launch {
                    if (remaining.isPositive()) delay(remaining)
                    dismiss(context, entry)
                }
            }
        }
    }

    private fun dismiss(context: Context, entry: TrackedActivity) {
        NotificationManagerCompat.from(context).cancel(entry.notificationId)
        entry.status.value = LiveActivityStatus.Dismissed
        tracked.remove(entry.handle.id)
        publishActivities()
        persistSnapshot()
    }

    @Suppress("UNCHECKED_CAST")
    private fun renderContent(
        attributes: LiveActivityAttributes,
        state: LiveActivityContentState,
    ): LiveActivityNotificationContent {
        val attributed = attributedRenderers[state::class]
            as? AttributedLiveActivityRenderer<LiveActivityAttributes, LiveActivityContentState>
        if (attributed != null) return attributed.render(attributes, state)
        val plain = renderers[state::class]
            as? LiveActivityRenderer<LiveActivityContentState>
            ?: FALLBACK_RENDERER
        return plain.render(state)
    }

    private fun refreshAuthorization() {
        val context = appContext ?: return
        _areActivitiesEnabled.value =
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun publishActivities() {
        _activities.value = tracked.values.map { it.handle }
    }

    private fun persistSnapshot() {
        val activeStore = store ?: return
        scope.launch {
            runCatching { activeStore.save(tracked.values.map { it.toPersisted() }) }
        }
    }

    private suspend fun restore() {
        try {
            val records = store?.load().orEmpty()
            var maxNotificationId = 0
            for (record in records) {
                runCatching { rebuild(record) }.getOrNull()?.let { entry ->
                    tracked[record.id] = entry
                    if (record.notificationId > maxNotificationId) {
                        maxNotificationId = record.notificationId
                    }
                }
            }
            if (maxNotificationId >= notificationIds.get()) {
                notificationIds.set(maxNotificationId + 1)
            }
            publishActivities()
        } catch (_: Throwable) {
            // Best-effort restore: a corrupt cache must not break the manager.
        } finally {
            restoreSignal.complete(Unit)
        }
    }

    private fun rebuild(record: PersistedActivity): TrackedActivity {
        val attributes = ActivityCodec.decodeAttributes(record.attributesClass, record.attributesJson)
        val state = ActivityCodec.decodeState(record.stateClass, record.stateJson)
        val stateFlow = MutableStateFlow(state)
        val statusFlow = MutableStateFlow(LiveActivityStatus.valueOf(record.status))
        val pushTokenFlow = MutableStateFlow<String?>(null)
        val handle = LiveActivity(
            id = record.id,
            attributes = attributes,
            state = stateFlow.asStateFlow(),
            pushToken = pushTokenFlow.asStateFlow(),
            status = statusFlow.asStateFlow(),
        )
        val config = LiveActivityConfig(
            pushType = if (record.pushType == PUSH_TYPE_TOKEN) PushType.Token else PushType.None,
            staleAfter = record.staleAfterMillis?.milliseconds,
            androidChannelId = record.channelId,
            androidSmallIconResName = record.smallIconResName,
        )
        return TrackedActivity(handle, record.notificationId, stateFlow, statusFlow, config)
    }

    private fun TrackedActivity.toPersisted(): PersistedActivity = PersistedActivity(
        id = handle.id,
        notificationId = notificationId,
        attributesClass = handle.attributes::class.java.name,
        attributesJson = ActivityCodec.encode(handle.attributes),
        stateClass = state.value::class.java.name,
        stateJson = ActivityCodec.encode(state.value),
        status = status.value.name,
        channelId = config.androidChannelId,
        smallIconResName = config.androidSmallIconResName,
        pushType = if (config.pushType is PushType.Token) PUSH_TYPE_TOKEN else PUSH_TYPE_NONE,
        staleAfterMillis = config.staleAfter?.inWholeMilliseconds,
    )

    private fun notInitialized(): Throwable = IllegalStateException(
        "LiveActivityManager.init(context) must be called from Application.onCreate().",
    )

    private class TrackedActivity(
        val handle: LiveActivity<*, *>,
        val notificationId: Int,
        val state: MutableStateFlow<LiveActivityContentState>,
        val status: MutableStateFlow<LiveActivityStatus>,
        val config: LiveActivityConfig,
    )

    private val FALLBACK_RENDERER: LiveActivityRenderer<LiveActivityContentState> =
        LiveActivityRenderer { state ->
            LiveActivityNotificationContent(title = "Live Activity", text = state.toString())
        }
}

private const val STARTING_NOTIFICATION_ID: Int = 7000
private const val PUSH_TYPE_TOKEN: String = "Token"
private const val PUSH_TYPE_NONE: String = "None"
