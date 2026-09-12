package dev.cl0ud9.manager.platform.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.cl0ud9.manager.MainActivity
import dev.cl0ud9.manager.R

private const val CHANNEL_ID = "updates"
private const val NOTIFICATION_ID = 1001

// local notification for a pending update found by ManifestCheckWorker, section 24 + 44.4 of the
// spec - WorkManager is the fallback so this fires independent of whether FCM is ever wired up
object UpdateNotifier {
    fun ensureChannel(context: Context) {
        val channel =
            NotificationChannel(CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifies when a new version is available for a catalog app"
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyPendingUpdates(
        context: Context,
        count: Int,
    ) {
        // notification permission is optional (amendment 44.4 - onboarding does not block on it), so
        // a declined/never-granted permission means silently skipping the notification, not a failure.
        // Checked inline, not via a helper function - lint's flow analysis for NotificationManagerCompat
        // .notify() doesn't trace a permission check across a function boundary.
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val openApp =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(if (count == 1) "1 update available" else "$count updates available")
                .setContentText("Tap to see what's new.")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
