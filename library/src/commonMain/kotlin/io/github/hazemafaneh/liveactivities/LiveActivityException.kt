package io.github.hazemafaneh.liveactivities

/**
 * Base type for every failure surfaced by [LiveActivityManager].
 *
 * These are never thrown across the public API — they are returned inside [Result.failure].
 */
public sealed class LiveActivityException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Live Activities are unavailable on this OS version (Android < 16 or iOS < 16.2). */
    public class NotSupportedOnPlatform(
        message: String = "Live Activities are not supported on this platform version.",
    ) : LiveActivityException(message)

    /** The user has disabled Live Activities (iOS) or notifications (Android) for this app. */
    public class NotAuthorized(
        message: String = "Live Activities are not authorized. Ask the user to enable them in settings.",
    ) : LiveActivityException(message)

    /** iOS rejected the request because too many Live Activities are already running. */
    public class BudgetExceeded(
        message: String = "The system Live Activity budget has been exceeded.",
    ) : LiveActivityException(message)

    /** No running activity matches the supplied id. */
    public class ActivityNotFound(
        public val activityId: String,
    ) : LiveActivityException("No Live Activity found with id '$activityId'.")

    /** The serialized content state exceeds the platform payload limit (4 KB on iOS). */
    public class PayloadTooLarge(
        public val sizeBytes: Int,
        public val limitBytes: Int,
    ) : LiveActivityException("Payload of $sizeBytes bytes exceeds the $limitBytes byte limit.")

    /** The attributes or content state could not be serialized or deserialized. */
    public class SerializationFailed(
        message: String,
        cause: Throwable? = null,
    ) : LiveActivityException(message, cause)
}
