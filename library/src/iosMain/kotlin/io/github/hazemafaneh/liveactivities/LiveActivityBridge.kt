package io.github.hazemafaneh.liveactivities

/**
 * The contract between this library's Kotlin code and `ActivityKit`.
 *
 * `ActivityKit` is a Swift-only framework with no Objective-C surface, so it cannot be reached
 * through Kotlin/Native cinterop. Instead, the companion `KMPLiveActivities` Swift package
 * implements this interface against `ActivityKit` and installs it once at launch via
 * [LiveActivityManager.register].
 *
 * All methods are fire-and-forget: results and asynchronous events flow back through the
 * `notify*` functions on [LiveActivityManager].
 */
public interface LiveActivityBridge {

    /** Whether the user currently permits Live Activities (`ActivityAuthorizationInfo`). */
    public fun areActivitiesEnabled(): Boolean

    /**
     * Requests a new Live Activity.
     *
     * @param activityId the library-assigned id; echo it back via `notifyStartResult`.
     * @param attributesTypeName the fully-qualified name of the attributes type.
     * @param attributesJson the JSON-encoded attributes.
     * @param contentStateJson the JSON-encoded initial content state.
     * @param staleAfterSeconds seconds until the activity goes stale, or `0.0` for none.
     * @param requestPushToken whether to observe and report an APNs push token.
     */
    public fun start(
        activityId: String,
        attributesTypeName: String,
        attributesJson: String,
        contentStateJson: String,
        staleAfterSeconds: Double,
        requestPushToken: Boolean,
    )

    /** Updates a running activity with a new JSON-encoded content state. */
    public fun update(activityId: String, contentStateJson: String, staleAfterSeconds: Double)

    /**
     * Ends a running activity.
     *
     * @param dismissalSeconds `0.0` for immediate dismissal, otherwise seconds from now.
     */
    public fun end(activityId: String, finalContentStateJson: String?, dismissalSeconds: Double)
}
