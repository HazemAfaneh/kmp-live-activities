package io.github.hazemafaneh.liveactivities

/**
 * A [LiveActivityRenderer] for the common case: a title, body text, and a
 * `NotificationCompat.ProgressStyle` configured from state.
 *
 * ProgressStyle is **always** attached to the produced content — even on the first call where
 * `progress` is naturally 0 — because Android 16 only promotes notifications that already carry
 * a supported style at post time. Defaulting [progress] to 0 (or [progressStyle] to one with
 * `isIndeterminate = true`) is therefore not just a convenience but a promotion prerequisite.
 *
 * ```kotlin
 * LiveActivityManager.registerRenderer(
 *     DeliveryState::class,
 *     DefaultProgressRenderer(
 *         title = { "Order from Pizza Hut" },
 *         text = { "ETA ${'$'}{it.etaMinutes} min" },
 *         progressStyle = { state -> ProgressStyleData(progress = state.percentComplete) },
 *     ),
 * )
 * ```
 *
 * @param title extracts the notification title from the state.
 * @param text extracts the main notification text from the state.
 * @param progressStyle extracts the [ProgressStyleData] from the state. Defaults to a 0-progress
 *   style so the first post is already promotion-eligible.
 * @param subText extracts optional short text shown next to the app name.
 * @param statusChip extracts an optional per-update status-bar chip override.
 */
public class DefaultProgressRenderer<S : LiveActivityContentState>(
    private val title: (S) -> String,
    private val text: (S) -> String,
    private val progressStyle: (S) -> ProgressStyleData = { ProgressStyleData(progress = 0) },
    private val subText: (S) -> String? = { null },
    private val statusChip: (S) -> StatusChipConfig? = { null },
) : LiveActivityRenderer<S> {

    override fun render(state: S): LiveActivityNotificationContent {
        val raw = progressStyle(state)
        val sanitized = raw.copy(progress = raw.progress.coerceIn(0, PROGRESS_MAX))
        return LiveActivityNotificationContent(
            title = title(state),
            text = text(state),
            progressStyle = sanitized,
            subText = subText(state),
            statusChip = statusChip(state),
        )
    }

    private companion object {
        const val PROGRESS_MAX: Int = 100
    }
}
