package io.github.hazemafaneh.liveactivities

/**
 * Vendor-neutral entry point for applying server-pushed Live Update content on Android.
 *
 * KMP-LiveActivities deliberately does not depend on Firebase or any messaging SDK. Call this
 * from your own `FirebaseMessagingService.onMessageReceived` — or any other push pipeline —
 * with the activity id and the JSON-encoded new content state carried in the message payload.
 *
 * ```kotlin
 * class MyMessagingService : FirebaseMessagingService() {
 *     override fun onMessageReceived(message: RemoteMessage) {
 *         val id = message.data["activityId"] ?: return
 *         val stateJson = message.data["state"] ?: return
 *         runBlocking { LiveActivityPushHandler.handleStateUpdate(id, stateJson) }
 *     }
 * }
 * ```
 */
public object LiveActivityPushHandler {

    /**
     * Applies a server-pushed content-state update to a running Live Update.
     *
     * @param activityId the [LiveActivity.id] carried in the push payload.
     * @param stateJson the new content state, JSON-encoded with the schema kotlinx serialization
     *   produces for the activity's state type.
     * @return [Unit] on success, or a [LiveActivityException] on failure.
     */
    public suspend fun handleStateUpdate(activityId: String, stateJson: String): Result<Unit> =
        LiveActivityManager.applyRemoteUpdate(activityId, stateJson)
}
