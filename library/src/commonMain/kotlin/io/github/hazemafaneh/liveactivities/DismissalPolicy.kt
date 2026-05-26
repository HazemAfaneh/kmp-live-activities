@file:OptIn(ExperimentalTime::class)

package io.github.hazemafaneh.liveactivities

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Controls how long a Live Activity stays visible after it is ended. */
public sealed interface DismissalPolicy {

    /** Remove the activity from the screen immediately. */
    public data object Immediate : DismissalPolicy

    /** Keep the activity visible for [duration] after it ends, then remove it. */
    public data class After(public val duration: Duration) : DismissalPolicy

    /** Keep the activity visible until [instant], then remove it. */
    public data class At(public val instant: Instant) : DismissalPolicy
}
