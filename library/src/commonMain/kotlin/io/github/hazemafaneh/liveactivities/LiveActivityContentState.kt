package io.github.hazemafaneh.liveactivities

/**
 * Marker interface for the *dynamic* content of a Live Activity.
 *
 * Implement this on your own `@Serializable` data class. The content state is replaced on every
 * [LiveActivityManager.update] call and drives what the user sees on the Lock Screen, in the
 * Dynamic Island, or in the Android notification.
 *
 * The serialized state must stay within the platform payload limit (4 KB on iOS); exceeding it
 * fails the update with [LiveActivityException.PayloadTooLarge].
 *
 * ```kotlin
 * @Serializable
 * data class DeliveryState(
 *     val status: String,
 *     val etaMinutes: Int,
 *     val driverName: String?,
 * ) : LiveActivityContentState
 * ```
 *
 * @see LiveActivityAttributes for the part that stays constant.
 */
public interface LiveActivityContentState
