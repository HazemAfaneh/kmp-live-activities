package io.github.hazemafaneh.liveactivities

/** How a Live Activity receives updates. */
public sealed interface PushType {

    /** Updates are delivered only locally via [LiveActivityManager.update]. The default. */
    public data object None : PushType

    /**
     * Requests an APNs push token so a server can update the activity remotely.
     *
     * The token is delivered through [LiveActivity.pushToken]. Has no effect on Android, where
     * remote updates use FCM and a data message handled by your messaging service.
     */
    public data object Token : PushType
}
