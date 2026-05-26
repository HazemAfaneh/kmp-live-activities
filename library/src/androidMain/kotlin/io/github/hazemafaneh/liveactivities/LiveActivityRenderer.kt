package io.github.hazemafaneh.liveactivities

/**
 * Converts a content state into the data shown in an Android Live Update notification.
 *
 * Register an implementation with [LiveActivityManager.registerRenderer] before starting an
 * activity of the matching state type. If no renderer is registered, a generic fallback that
 * shows the state's `toString()` is used.
 *
 * Android-only: iOS Live Activities are rendered by the companion Swift package in SwiftUI.
 */
public fun interface LiveActivityRenderer<S : LiveActivityContentState> {

    /** Maps [state] to the notification content to display. */
    public fun render(state: S): LiveActivityNotificationContent
}
