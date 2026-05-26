package io.github.hazemafaneh.liveactivities

/**
 * Same role as [LiveActivityRenderer], but also receives the activity's [LiveActivityAttributes]
 * so the rendered content can reference constants captured at start time (vendor name, order id,
 * item headline, ...) without duplicating them inside every state update.
 *
 * Register with [LiveActivityManager.registerAttributedRenderer]. When both an attributed and a
 * plain renderer are registered for the same state type, the attributed renderer wins.
 */
public fun interface AttributedLiveActivityRenderer<A : LiveActivityAttributes, S : LiveActivityContentState> {

    /** Maps [attributes] + [state] to the notification content to display. */
    public fun render(attributes: A, state: S): LiveActivityNotificationContent
}
