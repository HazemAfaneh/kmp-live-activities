package io.github.hazemafaneh.liveactivities

/**
 * Android-specific extension to [LiveActivityConfig].
 *
 * Covers fields that have no iOS analogue: the notification channel's user-visible
 * metadata and the status-bar chip configuration introduced by Android 16 Live Updates.
 *
 * The Android channel ID itself is still read from [LiveActivityConfig.androidChannelId];
 * the duplicate [channelId] field here is reserved for a future migration where the flat
 * field will be removed.
 */
public data class LiveActivityAndroidConfig(
    public val channelId: String = "live_activities",
    public val channelName: String = "Live Activities",
    public val channelDescription: String? = null,
    public val statusChip: StatusChipConfig? = null,
)

/** Configures the Android 16 status-bar chip that accompanies a promoted Live Update. */
public sealed interface StatusChipConfig {

    /**
     * Short critical text shown in the status-bar chip.
     *
     * Up to 7 characters render in full; the chip is hard-capped at 96dp width so longer
     * strings are truncated.
     */
    public data class CriticalText(public val text: String) : StatusChipConfig

    /** Chronometer / countdown chip driven by `setUsesChronometer` + `setWhen`. */
    public data class Chronometer(
        public val baseEpochMillis: Long,
        public val countDown: Boolean = false,
        public val showWhen: Boolean = true,
    ) : StatusChipConfig
}
