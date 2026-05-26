package io.github.hazemafaneh.liveactivities

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * A foreground service that keeps the hosting process alive for long-running Live Updates.
 *
 * It is opt-in: enable it with [LiveActivityManager.setForegroundServiceEnabled] while the app
 * is in the foreground. On Android 14+ it runs as a `shortService`, which the system limits to
 * a few minutes — treat extended background execution as best-effort.
 */
public class LiveActivityForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        return START_STICKY
    }

    private fun startInForeground() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW,
        ).setName("Live Activities service").build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Live Activities running")
            .setSmallIcon(applicationInfo.icon)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Companion entry points for starting and stopping the service. */
    public companion object {
        private const val CHANNEL_ID: String = "live_activities_service"
        private const val NOTIFICATION_ID: Int = 6999

        /** Starts the service. Must be called while the app is in the foreground. */
        public fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LiveActivityForegroundService::class.java),
            )
        }

        /** Stops the service. */
        public fun stop(context: Context) {
            context.stopService(Intent(context, LiveActivityForegroundService::class.java))
        }
    }
}
