package io.github.hazemafaneh.liveactivities.internal

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.hazemafaneh.liveactivities.LiveActivityConfig
import io.github.hazemafaneh.liveactivities.LiveActivityDismissReceiver
import io.github.hazemafaneh.liveactivities.LiveActivityNotificationContent
import io.github.hazemafaneh.liveactivities.ProgressStyleData
import io.github.hazemafaneh.liveactivities.StatusChipConfig
import io.github.hazemafaneh.liveactivities.requestPromotion

/**
 * Builds [Notification]s for Live Updates using `NotificationCompat.ProgressStyle` and the
 * Android 16 promotion APIs.
 *
 * Promotion to the status bar + top-of-shade requires *all* of the following to be true at
 * post time; this builder is the single place that enforces them:
 *
 *  - Channel importance is at least `IMPORTANCE_DEFAULT` (we default to it).
 *  - The notification is ongoing.
 *  - A `ProgressStyle` (or `CallStyle` / `BigTextStyle`) is attached — never a `RemoteViews`.
 *  - `setRequestPromotedOngoing(true)` is called.
 *  - The app declares both `POST_NOTIFICATIONS` and `POST_PROMOTED_NOTIFICATIONS` in the
 *    merged manifest, and `POST_NOTIFICATIONS` has been granted at runtime on API 33+.
 *
 * The first four are this object's job; the last is the consumer's.
 */
internal object AndroidNotifications {

    private const val TAG: String = "LiveActivities"

    /** Creates (or updates) the notification channel the Live Update notifications post to. */
    fun ensureChannel(context: Context, config: LiveActivityConfig) {
        val channel = NotificationChannelCompat.Builder(
            config.androidChannelId,
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        )
            .setName(config.androidConfig.channelName)
            .apply { config.androidConfig.channelDescription?.let(::setDescription) }
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Builds a notification for [content] using `NotificationCompat.ProgressStyle` and asks
     * Android 16 to promote it via [NotificationCompat.Builder.requestPromotion].
     *
     * Returns `null` only when no usable title can be derived (renderer returned blank AND the
     * application label is also blank) — caller should treat that as a fatal misconfiguration.
     */
    fun build(
        context: Context,
        config: LiveActivityConfig,
        content: LiveActivityNotificationContent,
        activityId: String,
        notificationId: Int,
    ): BuiltNotification? {
        val iconRes = resolveSmallIcon(context, config.androidSmallIconResName)
        val title = resolveTitle(context, content.title) ?: run {
            Log.e(
                TAG,
                "Live Activity ${'$'}activityId has no title and the application label is blank — " +
                    "refusing to post.",
            )
            return null
        }

        val builder = NotificationCompat.Builder(context, config.androidChannelId)
            .setContentTitle(title)
            .setContentText(content.text)
            .setSmallIcon(iconRes)
            .setOnlyAlertOnce(true)
            .setDeleteIntent(buildDeleteIntent(context, activityId, notificationId))
            .requestPromotion()

        content.subText?.let(builder::setSubText)

        val chip = content.statusChip ?: config.androidConfig.statusChip
        applyStatusChip(builder, chip)

        val progress = content.progressStyle
        val promotable: Boolean
        if (progress != null) {
            builder.setStyle(buildProgressStyle(context, progress))
            promotable = true
        } else {
            Log.w(
                TAG,
                "Live Activity ${'$'}activityId has no progressStyle — Android 16 will NOT promote " +
                    "this notification. Falling back to a standard ongoing notification.",
            )
            promotable = false
        }

        val notification = builder.build()
        return BuiltNotification(notification = notification, promotionRequested = promotable)
    }

    private fun buildProgressStyle(
        context: Context,
        data: ProgressStyleData,
    ): NotificationCompat.ProgressStyle {
        val style = NotificationCompat.ProgressStyle()
            .setProgress(data.progress.coerceIn(0, PROGRESS_MAX))
            .setProgressIndeterminate(data.isIndeterminate)

        if (data.segments.isNotEmpty()) {
            style.setProgressSegments(
                data.segments.map { seg ->
                    NotificationCompat.ProgressStyle.Segment(seg.length).apply {
                        seg.color?.let(::setColor)
                    }
                },
            )
        } else {
            // ProgressStyle requires at least one segment to render a track.
            style.setProgressSegments(
                listOf(NotificationCompat.ProgressStyle.Segment(PROGRESS_MAX)),
            )
        }

        if (data.points.isNotEmpty()) {
            // NotificationCompat.ProgressStyle.Point currently exposes color only; iconResId on
            // the data class is forward-compat for the day per-point icons land in androidx.core.
            style.setProgressPoints(
                data.points.map { point ->
                    NotificationCompat.ProgressStyle.Point(point.position).apply {
                        point.color?.let(::setColor)
                    }
                },
            )
        }

        data.startIconResId?.let {
            style.setProgressStartIcon(IconCompat.createWithResource(context, it))
        }
        data.endIconResId?.let {
            style.setProgressEndIcon(IconCompat.createWithResource(context, it))
        }
        data.trackerIconResId?.let {
            style.setProgressTrackerIcon(IconCompat.createWithResource(context, it))
        }

        return style
    }

    private fun applyStatusChip(builder: NotificationCompat.Builder, chip: StatusChipConfig?) {
        when (chip) {
            null -> Unit
            is StatusChipConfig.CriticalText -> builder.setShortCriticalText(chip.text)
            is StatusChipConfig.Chronometer -> {
                builder
                    .setWhen(chip.baseEpochMillis)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(chip.countDown)
                    .setShowWhen(chip.showWhen)
            }
        }
    }

    private fun buildDeleteIntent(
        context: Context,
        activityId: String,
        notificationId: Int,
    ): PendingIntent {
        val intent = Intent(context, LiveActivityDismissReceiver::class.java).apply {
            // Explicit package binding so the broadcast can't be hijacked by another app.
            setPackage(context.packageName)
            putExtra(LiveActivityDismissReceiver.EXTRA_ACTIVITY_ID, activityId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun resolveTitle(context: Context, candidate: String): String? {
        if (candidate.isNotBlank()) return candidate
        val label = context.applicationInfo.loadLabel(context.packageManager)?.toString()
        return label?.takeIf { it.isNotBlank() }
    }

    private fun resolveSmallIcon(context: Context, resName: String): Int {
        val id = context.resources.getIdentifier(resName, "drawable", context.packageName)
        return if (id != 0) id else context.applicationInfo.icon
    }

    /** Result of [build]; carries the notification plus whether promotion was requested. */
    data class BuiltNotification(
        val notification: Notification,
        val promotionRequested: Boolean,
    )

    private const val PROGRESS_MAX: Int = 100
}

/**
 * Logs whether the just-posted notification asked the system for Android 16 promotion.
 *
 * Drives the runtime-verification log line — pair this with
 * `adb shell dumpsys notification --noredact | grep mPromotedOngoing` to confirm the system
 * actually honoured the request.
 */
internal fun logPostResult(activityId: String, promotionRequested: Boolean) {
    val sdkSupportsPromotion = Build.VERSION.SDK_INT >= PROMOTION_SDK_INT
    Log.d(
        "LiveActivities",
        "Posted Live Activity ${'$'}activityId — promotionRequested=${'$'}promotionRequested, " +
            "sdkSupportsPromotion=${'$'}sdkSupportsPromotion (SDK ${'$'}{Build.VERSION.SDK_INT})",
    )
}

private const val PROMOTION_SDK_INT: Int = 36
