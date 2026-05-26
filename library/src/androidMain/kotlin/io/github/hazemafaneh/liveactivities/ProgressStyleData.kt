package io.github.hazemafaneh.liveactivities

import androidx.annotation.ColorInt

/**
 * Rich progress-bar configuration used by Android 16's `NotificationCompat.ProgressStyle`.
 *
 * @property progress current value in `0..100`. Ignored when [isIndeterminate] is true.
 * @property segments coloured background segments laid out left-to-right; lengths sum to 100.
 *   Each segment paints its own slice of the track and is what gives multi-stage progress its
 *   visual identity. An empty list draws a single neutral track.
 * @property points discrete markers along the track at `position in 0..100`. Useful for stage
 *   boundaries (e.g. "preparing", "on the way", "delivered").
 * @property startIconResId optional resource icon drawn at the start of the track.
 * @property endIconResId optional resource icon drawn at the end of the track.
 * @property trackerIconResId optional resource icon that follows the current progress value.
 * @property isIndeterminate when true, renders an indeterminate animation and ignores [progress].
 */
public data class ProgressStyleData(
    public val progress: Int = 0,
    public val segments: List<Segment> = emptyList(),
    public val points: List<Point> = emptyList(),
    public val startIconResId: Int? = null,
    public val endIconResId: Int? = null,
    public val trackerIconResId: Int? = null,
    public val isIndeterminate: Boolean = false,
) {
    public data class Segment(
        public val length: Int,
        @param:ColorInt public val color: Int? = null,
    )

    public data class Point(
        public val position: Int,
        @param:ColorInt public val color: Int? = null,
        public val iconResId: Int? = null,
    )
}
