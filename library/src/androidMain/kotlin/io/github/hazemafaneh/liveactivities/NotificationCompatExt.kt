package io.github.hazemafaneh.liveactivities

import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Applies every flag required for Android 16's Live Update promotion in one call.
 *
 * Specifically:
 *  - `setOngoing(true)` — promotion rejects non-ongoing notifications outright.
 *  - `setRequestPromotedOngoing(true)` — the actual opt-in for status-bar chip + top-of-shade
 *    treatment. The check is guarded with `Build.VERSION.SDK_INT >= 36` so it's explicit at the
 *    call site that this is the API 36 path (the NotificationCompat shim is a no-op on older
 *    versions, but the guard makes the intent unmistakable to readers).
 *
 * Use this when writing a custom renderer or builder. The library's default builder already
 * calls it for you.
 */
public fun NotificationCompat.Builder.requestPromotion(): NotificationCompat.Builder {
    setOngoing(true)
    if (Build.VERSION.SDK_INT >= LIVE_UPDATE_SDK_INT) {
        setRequestPromotedOngoing(true)
    }
    return this
}

private const val LIVE_UPDATE_SDK_INT: Int = 36
