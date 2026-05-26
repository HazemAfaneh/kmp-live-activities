package io.github.hazemafaneh.liveactivities

import kotlinx.coroutines.flow.StateFlow

/**
 * The single entry point for creating, updating, and ending Live Activities.
 *
 * The same API backs iOS Live Activities (Dynamic Island + Lock Screen) and Android 16+
 * Live Updates (`Notification.ProgressStyle`). Every operation returns a [Result]; failures are
 * reported as [LiveActivityException] inside [Result.failure] and are never thrown.
 *
 * On Android, call `LiveActivityManager.init(context)` from `Application.onCreate()` before use.
 * On iOS, initialization is lazy; wire up the companion Swift package once in your app entry point.
 */
public expect object LiveActivityManager {

    /** Every Live Activity currently tracked by this process, including stale and ended ones. */
    public val activities: StateFlow<List<LiveActivity<*, *>>>

    /** Whether the user currently permits Live Activities for this app. */
    public val areActivitiesEnabled: StateFlow<Boolean>

    /**
     * Starts a new Live Activity.
     *
     * @param attributes the immutable metadata for the activity.
     * @param initialState the first content state to display.
     * @param config optional per-activity configuration.
     * @return a [LiveActivity] handle on success, or a [LiveActivityException] on failure.
     */
    public suspend fun <A : LiveActivityAttributes, S : LiveActivityContentState> start(
        attributes: A,
        initialState: S,
        config: LiveActivityConfig = LiveActivityConfig(),
    ): Result<LiveActivity<A, S>>

    /**
     * Replaces the content state of a running activity.
     *
     * @param activityId the [LiveActivity.id] of the activity to update.
     * @param newState the new content state to display.
     * @return [Unit] on success, or a [LiveActivityException] on failure.
     */
    public suspend fun <S : LiveActivityContentState> update(
        activityId: String,
        newState: S,
    ): Result<Unit>

    /**
     * Ends a running activity.
     *
     * @param activityId the [LiveActivity.id] of the activity to end.
     * @param dismissalPolicy when the activity should be removed from the screen.
     * @return [Unit] on success, or a [LiveActivityException] on failure.
     */
    public suspend fun end(
        activityId: String,
        dismissalPolicy: DismissalPolicy = DismissalPolicy.Immediate,
    ): Result<Unit>

    /**
     * Ends every running activity.
     *
     * @param dismissalPolicy when the activities should be removed from the screen.
     * @return [Unit] on success, or a [LiveActivityException] on failure.
     */
    public suspend fun endAll(
        dismissalPolicy: DismissalPolicy = DismissalPolicy.Immediate,
    ): Result<Unit>
}
