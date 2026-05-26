package io.github.hazemafaneh.liveactivities

import kotlin.time.Duration

/**
 * Per-activity configuration passed to [LiveActivityManager.start].
 *
 * @property pushType whether to request a remote push token. Defaults to [PushType.None].
 * @property staleAfter if set, the activity is automatically marked [LiveActivityStatus.Stale]
 *   when no update arrives within this duration. `null` disables staleness tracking.
 * @property androidChannelId the notification channel id used for the Android Live Update.
 *   Ignored on iOS.
 * @property androidSmallIconResName the resource name of the small status-bar icon used for the
 *   Android notification. Ignored on iOS.
 * @property androidConfig extra Android-only knobs — channel name/description and status-bar
 *   chip configuration. Ignored on iOS.
 */
public data class LiveActivityConfig(
    public val pushType: PushType = PushType.None,
    public val staleAfter: Duration? = null,
    public val androidChannelId: String = "live_activities",
    public val androidSmallIconResName: String = "ic_live_activity",
    public val androidConfig: LiveActivityAndroidConfig = LiveActivityAndroidConfig(),
)
