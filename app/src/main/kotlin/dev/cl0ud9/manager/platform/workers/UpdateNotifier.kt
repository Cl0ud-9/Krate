package dev.cl0ud9.manager.platform.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.cl0ud9.manager.EXTRA_TARGET_ROUTE
import dev.cl0ud9.manager.MainActivity
import dev.cl0ud9.manager.R

// "_v2", not just "updates": a notification channel's importance can't be changed after it's first
// created under a given id - Android ignores createNotificationChannel() for an id that already
// exists, and a delete+recreate under the SAME id doesn't reliably reset it either (Android restores
// the channel's prior effective importance rather than honoring the new request - confirmed live via
// `dumpsys notification` on the sibling "downloads" channel, which stayed stuck at its original
// importance through exactly that delete+recreate dance). This channel predates IMPORTANCE_HIGH
// being requested here, so it was stuck at whatever it originally shipped with; a new id is the only
// reliable fix - the old "updates" channel is simply orphaned, which is harmless
private const val CHANNEL_ID = "updates_v2"
private const val PENDING_UPDATES_NOTIFICATION_ID = 1001
private const val MANAGER_UPDATE_NOTIFICATION_ID = 1002
private const val UPDATE_ALL_RESULT_NOTIFICATION_ID = 1003
private const val MANAGER_UPDATED_NOTIFICATION_ID = 1004
private const val STATE_PREFS = "update_notifier"
private const val KEY_PENDING_SIGNATURE = "pending_signature"
private const val KEY_MANAGER_VERSION = "manager_version"

// local notifications for update-related events the user might not be watching the app for -
// a pending catalog update found by ManifestCheckWorker (section 24 + 44.4 of the spec), a manager
// self-update becoming available (amendment 44.2), and an Update All batch finishing while
// backgrounded. WorkManager is the fallback path so the first of these fires independent of whether
// FCM is ever wired up
object UpdateNotifier {
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifies about catalog app updates, manager updates, and Update All results"
            }
        manager?.createNotificationChannel(channel)
    }

    // only alerts when the set of pending builds differs from the one last notified about - a
    // periodic check that finds the same updates still waiting stays quiet instead of buzzing again
    fun notifyPendingUpdates(
        context: Context,
        appNames: List<String>,
        signature: String,
        downloaded: Boolean,
    ) {
        val prefs = state(context)
        if (prefs.getString(KEY_PENDING_SIGNATURE, null) == signature) return
        prefs.edit().putString(KEY_PENDING_SIGNATURE, signature).apply()
        // names the apps instead of only counting them - "Videoapp (Patched) has an update" says what
        // to do with it at a glance, a bare "1 update available" doesn't
        val title = appNames.singleOrNull()?.let { "Update for $it" } ?: "${appNames.size} updates available"
        val action = if (downloaded) "Downloaded and ready to install." else "Tap to review and install."
        notify(
            context = context,
            id = PENDING_UPDATES_NOTIFICATION_ID,
            title = title,
            text = if (appNames.size > 1) "${appNames.joinToString(", ")}. $action" else action,
            targetRoute = "updates",
        )
    }

    // nothing pending anymore (installed, or the catalog withdrew it) - a leftover notification
    // would point at updates that no longer exist, and the next real one should alert again
    fun clearPendingUpdates(context: Context) {
        state(context).edit().remove(KEY_PENDING_SIGNATURE).apply()
        NotificationManagerCompat.from(context).cancel(PENDING_UPDATES_NOTIFICATION_ID)
    }

    // amendment 44.2: the periodic background check also compares the manager's own version now
    // that ManagerUpdateChecker exists, instead of only ever checking catalog apps. Once per version
    fun notifyManagerUpdateAvailable(
        context: Context,
        version: String,
    ) {
        val prefs = state(context)
        if (prefs.getString(KEY_MANAGER_VERSION, null) == version) return
        prefs.edit().putString(KEY_MANAGER_VERSION, version).apply()
        notify(
            context = context,
            id = MANAGER_UPDATE_NOTIFICATION_ID,
            title = "App Manager $version is available",
            text = "Tap to update now.",
            // Settings checks on open and offers the in-app Update button right there
            targetRoute = "settings",
        )
    }

    // after an in-app self-update Android has closed the app - this is the way back in
    fun notifyManagerUpdated(
        context: Context,
        version: String?,
    ) {
        NotificationManagerCompat.from(context).cancel(MANAGER_UPDATE_NOTIFICATION_ID)
        notify(
            context = context,
            id = MANAGER_UPDATED_NOTIFICATION_ID,
            title = version?.let { "App Manager updated to $it" } ?: "App Manager updated",
            text = "Tap to open.",
            targetRoute = "home",
        )
    }

    private fun state(context: Context): SharedPreferences =
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

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
                failed == 0 -> if (succeeded == 1) "App updated" else "All $succeeded apps updated"
                succeeded == 0 -> if (failed == 1) "Update failed" else "$failed updates failed"
                else -> "$succeeded updated, $failed failed"
            }
        notify(
            context = context,
            id = UPDATE_ALL_RESULT_NOTIFICATION_ID,
            title = title,
            text = if (failed == 0) "Everything is up to date." else "Tap to see what went wrong.",
            targetRoute = "updates",
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
        targetRoute: String,
    ) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) return

        ensureChannel(context)

        val openApp =
            PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_TARGET_ROUTE, targetRoute)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_update)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
