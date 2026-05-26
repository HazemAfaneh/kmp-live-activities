package io.github.hazemafaneh.liveactivities

import androidx.annotation.ColorInt

/**
 * Platform-neutral description of what an Android Live Update notification should show.
 *
 * @property title the notification title (bold first line). Must be non-blank for promotion; the
 *   builder falls back to the application label if it is.
 * @property text the main notification text.
 * @property progressStyle full progress-bar configuration. Whenever this is non-null the builder
 *   attaches a `NotificationCompat.ProgressStyle`, which is one of the styles the system requires
 *   for promotion. The library's [DefaultProgressRenderer] always populates this.
 * @property subText optional short text shown next to the app name.
 * @property statusChip optional per-update override of the status-bar chip. When `null`, the
 *   builder falls back to [LiveActivityAndroidConfig.statusChip] from the activity config.
 * @property accentColor optional ARGB color passed to `NotificationCompat.Builder.setColor`,
 *   tinting the app name and small-icon background in the shade and on the lock screen.
 */
public data class LiveActivityNotificationContent(
    public val title: String,
    public val text: String,
    public val progressStyle: ProgressStyleData? = null,
    public val subText: String? = null,
    public val statusChip: StatusChipConfig? = null,
    @param:ColorInt public val accentColor: Int? = null,
)
