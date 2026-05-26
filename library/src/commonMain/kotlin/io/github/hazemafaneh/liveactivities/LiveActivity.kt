package io.github.hazemafaneh.liveactivities

import kotlinx.coroutines.flow.StateFlow

/** Lifecycle state of a [LiveActivity]. */
public enum class LiveActivityStatus {
    /** The activity is visible and receiving updates. */
    Active,

    /** No update arrived before [LiveActivityConfig.staleAfter] elapsed; the UI may be dimmed. */
    Stale,

    /** The activity finished but is still briefly visible per its [DismissalPolicy]. */
    Ended,

    /** The activity has been removed from the screen. This is a terminal state. */
    Dismissed,
}

/**
 * A handle to a single running Live Activity (iOS) or Live Update notification (Android).
 *
 * Instances are created by [LiveActivityManager.start] and observed through their [StateFlow]
 * properties. A handle stays usable until [status] reaches [LiveActivityStatus.Dismissed].
 *
 * @param A the static attributes type.
 * @param S the dynamic content-state type.
 */
public class LiveActivity<A : LiveActivityAttributes, S : LiveActivityContentState> internal constructor(
    /**
     * Stable identifier, unique within the process. Pass this to [LiveActivityManager.update]
     * and [LiveActivityManager.end].
     */
    public val id: String,
    /** The immutable attributes this activity was started with. */
    public val attributes: A,
    /** The latest content state. Emits a new value on every successful update. */
    public val state: StateFlow<S>,
    /**
     * The APNs push token for this activity, or `null`.
     *
     * Always `null` on Android, and on iOS unless the activity was started with
     * [PushType.Token]. The value may arrive asynchronously after [LiveActivityManager.start]
     * returns, so observe the flow rather than reading it once.
     */
    public val pushToken: StateFlow<String?>,
    /** The current lifecycle status of this activity. */
    public val status: StateFlow<LiveActivityStatus>,
)
