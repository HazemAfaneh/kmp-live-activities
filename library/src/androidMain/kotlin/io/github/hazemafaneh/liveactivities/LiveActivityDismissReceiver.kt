package io.github.hazemafaneh.liveactivities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fires when the user swipes a promoted Live Activity off the shade.
 *
 * Wired up by the library: every notification it posts has a delete intent pointing at this
 * receiver. The handler only flips the activity's status to [LiveActivityStatus.Dismissed] and
 * emits on `LiveActivityManager.activities`; it deliberately does **not** re-post or restart
 * the activity — that decision belongs to the consuming app.
 *
 * Registered in the library's `AndroidManifest.xml` with `android:exported="false"`.
 */
public class LiveActivityDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID)
        if (activityId.isNullOrEmpty()) {
            Log.w(TAG, "Dismiss broadcast missing activity id; ignoring.")
            return
        }
        LiveActivityManager.handleSystemDismissal(activityId)
    }

    public companion object {
        public const val EXTRA_ACTIVITY_ID: String = "io.github.hazemafaneh.liveactivities.ACTIVITY_ID"
        private const val TAG: String = "LiveActivities"
    }
}
