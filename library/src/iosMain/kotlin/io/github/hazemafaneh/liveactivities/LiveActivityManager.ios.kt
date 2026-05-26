@file:OptIn(ExperimentalTime::class)

package io.github.hazemafaneh.liveactivities

import io.github.hazemafaneh.liveactivities.internal.PayloadCodec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * iOS implementation of [LiveActivityManager], backed by `ActivityKit` through the companion
 * `KMPLiveActivities` Swift package.
 *
 * The Swift package must call [register] once at launch (`KMPLiveActivities.register()`). Until
 * it does, every operation fails fast. Attributes and content states are serialized to JSON and
 * handed to the Swift bridge, which renders the UI in a SwiftUI Widget Extension.
 */
public actual object LiveActivityManager {

    private val tracked: MutableMap<String, TrackedActivity> = mutableMapOf()
    private val pendingStarts: MutableMap<String, CompletableDeferred<Result<Unit>>> =
        mutableMapOf()
    private val pushToStartTokens: MutableMap<String, MutableStateFlow<String?>> = mutableMapOf()
    private var bridge: LiveActivityBridge? = null

    private val _activities: MutableStateFlow<List<LiveActivity<*, *>>> =
        MutableStateFlow(emptyList())
    public actual val activities: StateFlow<List<LiveActivity<*, *>>> = _activities.asStateFlow()

    private val _areActivitiesEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    public actual val areActivitiesEnabled: StateFlow<Boolean> =
        _areActivitiesEnabled.asStateFlow()

    /**
     * Installs the `ActivityKit` [bridge]. Called once by `KMPLiveActivities.register()` from
     * the consuming app's Swift entry point.
     */
    public fun register(bridge: LiveActivityBridge) {
        this.bridge = bridge
        _areActivitiesEnabled.value = bridge.areActivitiesEnabled()
    }

    /** Observable push-to-start token for the attributes type named [attributesTypeName]. */
    public fun pushToStartToken(attributesTypeName: String): StateFlow<String?> =
        pushToStartTokens.getOrPut(attributesTypeName) { MutableStateFlow(null) }.asStateFlow()

    @Suppress("UNCHECKED_CAST")
    public actual suspend fun <A : LiveActivityAttributes, S : LiveActivityContentState> start(
        attributes: A,
        initialState: S,
        config: LiveActivityConfig,
    ): Result<LiveActivity<A, S>> {
        val activeBridge = bridge ?: return Result.failure(notRegistered())
        if (!activeBridge.areActivitiesEnabled()) {
            return Result.failure(LiveActivityException.NotAuthorized())
        }

        val attributesJson = runCatching { PayloadCodec.encode(attributes) }.getOrElse {
            return Result.failure(
                LiveActivityException.SerializationFailed("Failed to serialize attributes.", it),
            )
        }
        val stateJson = runCatching { PayloadCodec.encode(initialState) }.getOrElse {
            return Result.failure(
                LiveActivityException.SerializationFailed("Failed to serialize content state.", it),
            )
        }
        val payloadSize = PayloadCodec.byteSize(stateJson)
        if (payloadSize > MAX_PAYLOAD_BYTES) {
            return Result.failure(LiveActivityException.PayloadTooLarge(payloadSize, MAX_PAYLOAD_BYTES))
        }

        val id = "la_" + randomId()
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

        val pending = CompletableDeferred<Result<Unit>>()
        pendingStarts[id] = pending
        activeBridge.start(
            activityId = id,
            attributesTypeName = attributes::class.qualifiedName
                ?: attributes::class.simpleName
                ?: "LiveActivityAttributes",
            attributesJson = attributesJson,
            contentStateJson = stateJson,
            staleAfterSeconds = config.staleAfter?.inWholeSeconds?.toDouble() ?: 0.0,
            requestPushToken = config.pushType is PushType.Token,
        )

        val outcome = withTimeoutOrNull(START_TIMEOUT_MS) { pending.await() }
        pendingStarts.remove(id)
        if (outcome == null) {
            return Result.failure(IllegalStateException("Timed out starting the Live Activity."))
        }
        return outcome.fold(
            onSuccess = {
                tracked[id] = TrackedActivity(
                    handle = handle,
                    state = stateFlow as MutableStateFlow<LiveActivityContentState>,
                    status = statusFlow,
                    pushToken = pushTokenFlow,
                    config = config,
                    stateSerializer = PayloadCodec.serializerOf(initialState),
                )
                publishActivities()
                Result.success(handle)
            },
            onFailure = { Result.failure(it) },
        )
    }

    public actual suspend fun <S : LiveActivityContentState> update(
        activityId: String,
        newState: S,
    ): Result<Unit> {
        val activeBridge = bridge ?: return Result.failure(notRegistered())
        val entry = tracked[activityId]
            ?: return Result.failure(LiveActivityException.ActivityNotFound(activityId))
        val stateJson = runCatching { PayloadCodec.encode(newState) }.getOrElse {
            return Result.failure(
                LiveActivityException.SerializationFailed("Failed to serialize content state.", it),
            )
        }
        val payloadSize = PayloadCodec.byteSize(stateJson)
        if (payloadSize > MAX_PAYLOAD_BYTES) {
            return Result.failure(LiveActivityException.PayloadTooLarge(payloadSize, MAX_PAYLOAD_BYTES))
        }
        entry.state.value = newState
        entry.status.value = LiveActivityStatus.Active
        activeBridge.update(
            activityId = activityId,
            contentStateJson = stateJson,
            staleAfterSeconds = entry.config.staleAfter?.inWholeSeconds?.toDouble() ?: 0.0,
        )
        return Result.success(Unit)
    }

    public actual suspend fun end(
        activityId: String,
        dismissalPolicy: DismissalPolicy,
    ): Result<Unit> {
        val activeBridge = bridge ?: return Result.failure(notRegistered())
        val entry = tracked[activityId]
            ?: return Result.failure(LiveActivityException.ActivityNotFound(activityId))
        entry.status.value = LiveActivityStatus.Ended
        activeBridge.end(activityId, finalContentStateJson = null, dismissalSeconds(dismissalPolicy))
        return Result.success(Unit)
    }

    public actual suspend fun endAll(dismissalPolicy: DismissalPolicy): Result<Unit> {
        bridge ?: return Result.failure(notRegistered())
        tracked.keys.toList().forEach { end(it, dismissalPolicy) }
        return Result.success(Unit)
    }

    // --- Callbacks invoked by the Swift bridge ---------------------------------------------

    /** Reports the outcome of a [start] request. Echoes the `activityId` passed to the bridge. */
    public fun notifyStartResult(activityId: String, success: Boolean, errorKind: String?) {
        val pending = pendingStarts[activityId] ?: return
        if (success) {
            pending.complete(Result.success(Unit))
        } else {
            pending.complete(Result.failure(mapError(errorKind)))
        }
    }

    /** Reports a new (or cleared) APNs push token for a running activity. */
    public fun notifyPushToken(activityId: String, token: String?) {
        tracked[activityId]?.pushToken?.value = token
    }

    /** Reports a new push-to-start token for the named attributes type. */
    public fun notifyPushToStartToken(attributesTypeName: String, token: String?) {
        pushToStartTokens.getOrPut(attributesTypeName) { MutableStateFlow(null) }.value = token
    }

    /** Reports a lifecycle change for a running activity (an [LiveActivityStatus] name). */
    public fun notifyStatusChanged(activityId: String, status: String) {
        val entry = tracked[activityId] ?: return
        val parsed = runCatching { LiveActivityStatus.valueOf(status) }.getOrNull() ?: return
        entry.status.value = parsed
        if (parsed == LiveActivityStatus.Dismissed) {
            tracked.remove(activityId)
        }
        publishActivities()
    }

    /** Reports that the activity's content state changed outside [update] (e.g. a push). */
    public fun notifyStateChanged(activityId: String, contentStateJson: String) {
        val entry = tracked[activityId] ?: return
        val decoded = runCatching {
            PayloadCodec.json.decodeFromString(entry.stateSerializer, contentStateJson)
        }.getOrNull()
        (decoded as? LiveActivityContentState)?.let { entry.state.value = it }
    }

    /** Reports a change in whether the user permits Live Activities. */
    public fun notifyActivitiesEnabledChanged(enabled: Boolean) {
        _areActivitiesEnabled.value = enabled
    }

    // --- Internals -------------------------------------------------------------------------

    private fun dismissalSeconds(policy: DismissalPolicy): Double = when (policy) {
        DismissalPolicy.Immediate -> 0.0
        is DismissalPolicy.After -> policy.duration.inWholeSeconds.toDouble()
        is DismissalPolicy.At ->
            (policy.instant - Clock.System.now()).inWholeSeconds.toDouble().coerceAtLeast(0.0)
    }

    private fun mapError(kind: String?): Throwable = when (kind) {
        "unauthorized" -> LiveActivityException.NotAuthorized()
        "budget" -> LiveActivityException.BudgetExceeded()
        "unsupported" -> LiveActivityException.NotSupportedOnPlatform()
        "payloadTooLarge" -> LiveActivityException.PayloadTooLarge(MAX_PAYLOAD_BYTES, MAX_PAYLOAD_BYTES)
        else -> IllegalStateException("ActivityKit could not start the activity: ${kind ?: "unknown"}")
    }

    private fun publishActivities() {
        _activities.value = tracked.values.map { it.handle }
    }

    private fun randomId(): String = Random.nextLong().toULong().toString(16)

    private fun notRegistered(): Throwable = IllegalStateException(
        "The KMPLiveActivities Swift package is not registered. " +
            "Call KMPLiveActivities.register() once at app launch.",
    )

    private class TrackedActivity(
        val handle: LiveActivity<*, *>,
        val state: MutableStateFlow<LiveActivityContentState>,
        val status: MutableStateFlow<LiveActivityStatus>,
        val pushToken: MutableStateFlow<String?>,
        val config: LiveActivityConfig,
        val stateSerializer: KSerializer<Any>,
    )
}

private const val MAX_PAYLOAD_BYTES: Int = 4096
private const val START_TIMEOUT_MS: Long = 10_000
