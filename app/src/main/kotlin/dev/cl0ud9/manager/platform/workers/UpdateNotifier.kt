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
private const val PENDING_UPDATES_NOTIFICATION_ID = 1001
private const val MANAGER_UPDATE_NOTIFICATION_ID = 1002
private const val UPDATE_ALL_RESULT_NOTIFICATION_ID = 1003

// local notifications for update-related events the user might not be watching the app for -
// a pending catalog update found by ManifestCheckWorker (section 24 + 44.4 of the spec), a manager
// self-update becoming available (amendment 44.2), and an Update All batch finishing while
// backgrounded. WorkManager is the fallback path so the first of these fires independent of whether
// FCM is ever wired up
object UpdateNotifier {
    fun ensureChannel(context: Context) {
        val channel =
            NotificationChannel(CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifies about catalog app updates, manager updates, and Update All results"
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyPendingUpdates(
        context: Context,
        count: Int,
    ) {
        notify(
            context = context,
            id = PENDING_UPDATES_NOTIFICATION_ID,
            title = if (count == 1) "1 update available" else "$count updates available",
            text = "Tap to see what's new.",
        )
    }

    // amendment 44.2: the periodic background check also compares the manager's own version now
    // that ManagerUpdateChecker exists, instead of only ever checking catalog apps
    fun notifyManagerUpdateAvailable(
        context: Context,
        version: String,
    ) {
        notify(
            context = context,
            id = MANAGER_UPDATE_NOTIFICATION_ID,
            title = "Manager update available",
            text = "Version $version is ready on GitHub.",
        )
    }

    // Update All can run for a while across several apps - if the user backgrounds the app partway
    // through, the on-screen result summary (UpdateAllBar's Done state) goes unseen, so this is the
    // one install-completion path where a notification adds real value rather than duplicating
    // feedback the user is already looking at (a single app's install always needs an active
    // foreground confirmation dialog, so that path doesn't need its own notification)
    fun notifyUpdateAllResult(
        context: Context,
        succeeded: Int,
        failed: Int,
    ) {
        val title =
            when {
                failed == 0 -> "All $succeeded app${if (succeeded == 1) "" else "s"} updated"
                succeeded == 0 -> "Update all failed"
                else -> "$succeeded updated, $failed failed"
            }
        notify(
            context = context,
            id = UPDATE_ALL_RESULT_NOTIFICATION_ID,
            title = title,
            text = "Tap to see the details.",
        )
    }

    // notification permission is optional (amendment 44.4 - onboarding does not block on it), so
    // a declined/never-granted permission means silently skipping the notification, not a failure.
    // Checked inline, not via a helper function - lint's flow analysis for NotificationManagerCompat
    // .notify() doesn't trace a permission check across a function boundary.
    private fun notify(
        context: Context,
        id: Int,
        title: String,
        text: String,
    ) {
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
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
