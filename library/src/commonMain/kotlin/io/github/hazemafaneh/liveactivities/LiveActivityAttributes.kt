package io.github.hazemafaneh.liveactivities

/**
 * Marker interface for the *static* metadata of a Live Activity.
 *
 * Implement this on your own `@Serializable` data class. Attributes are set once when the
 * activity starts and never change for its lifetime — for example an order id or a
 * restaurant name.
 *
 * ```kotlin
 * @Serializable
 * data class DeliveryAttributes(
 *     val orderId: String,
 *     val restaurantName: String,
 * ) : LiveActivityAttributes
 * ```
 *
 * @see LiveActivityContentState for the part that *does* change over time.
 */
public interface LiveActivityAttributes
