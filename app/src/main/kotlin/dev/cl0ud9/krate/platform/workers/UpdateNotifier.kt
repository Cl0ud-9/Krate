package dev.cl0ud9.krate.platform.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.cl0ud9.krate.EXTRA_NOTIFICATION_ID
import dev.cl0ud9.krate.EXTRA_TARGET_ROUTE
import dev.cl0ud9.krate.KrateActivity
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.notifications.NotificationIcons
import dev.cl0ud9.krate.platform.notifications.krateContent
import dev.cl0ud9.krate.voice.KrateVoice
import dev.cl0ud9.krate.voice.Moment

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
private const val KRATE_UPDATE_NOTIFICATION_ID = 1002
private const val UPDATE_ALL_RESULT_NOTIFICATION_ID = 1003
internal const val KRATE_UPDATED_NOTIFICATION_ID = 1004
private const val STATE_PREFS = "update_notifier"
private const val KEY_PENDING_SIGNATURE = "pending_signature"
private const val KEY_PENDING_READY = "pending_ready"
private const val KEY_KRATE_VERSION = "krate_version"

// local notifications for update-related events the user might not be watching the app for -
// a pending catalog update found by ManifestCheckWorker (section 24 + 44.4 of the spec), a Krate
// self-update becoming available (amendment 44.2), and an Update All batch finishing while
// backgrounded. WorkManager is the fallback path so the first of these fires independent of whether
// FCM is ever wired up
object UpdateNotifier {
    fun ensureChannel(context: Context) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifies about app updates, Krate updates, and Update All results"
            }
        notifications?.createNotificationChannel(channel)
    }

    // only alerts when the set of pending builds differs from the one last notified about - a
    // periodic check that finds the same updates still waiting stays quiet instead of buzzing again
    fun notifyPendingUpdates(
        context: Context,
        apps: List<AppProfile>,
        signature: String,
        downloaded: Boolean,
    ) {
        val prefs = state(context)
        val sameUpdates = prefs.getString(KEY_PENDING_SIGNATURE, null) == signature
        val wasReady = sameUpdates && prefs.getBoolean(KEY_PENDING_READY, false)
        // the same updates stay quiet, unless they've since been downloaded - that's worth one more "ready" notice
        if (sameUpdates && wasReady == downloaded) return
        prefs
            .edit()
            .putString(KEY_PENDING_SIGNATURE, signature)
            .putBoolean(KEY_PENDING_READY, downloaded)
            .apply()
        // a "ready" notice whose file has gone (cache cleared) is corrected in place, never raised again once dismissed
        if (sameUpdates && !downloaded && !isShowing(context, PENDING_UPDATES_NOTIFICATION_ID)) return
        // names the apps instead of only counting them - "Mihon has an update" says what
        // to do with it at a glance, a bare "1 update available" doesn't
        val single = apps.singleOrNull()
        notify(
            context,
            PENDING_UPDATES_NOTIFICATION_ID,
            KrateNotification(
                headline = KrateVoice.line(Moment.UPDATES_WAITING),
                fact = pendingUpdatesText(apps.map { it.displayName }, downloaded),
                category = "Updates",
                targetRoute = "updates",
                actionLabel = if (single != null) "Update" else "Update all",
                largeIcon = single?.let { NotificationIcons.app(context, it) },
            ),
        )
    }

    private fun pendingUpdatesText(
        appNames: List<String>,
        downloaded: Boolean,
    ): String {
        val single = appNames.singleOrNull()
        return when {
            single != null && downloaded -> "An update for $single is downloaded and ready to install."
            single != null -> "$single has an update. Tap to review and install."
            downloaded -> "${appNames.size} updates: ${appNames.joinToString(", ")}. Downloaded and ready to install."
            else -> "${appNames.size} updates: ${appNames.joinToString(", ")}. Tap to review and install."
        }
    }

    // nothing pending anymore (installed, or the catalog withdrew it) - a leftover notification
    // would point at updates that no longer exist, and the next real one should alert again
    fun clearPendingUpdates(context: Context) {
        state(context)
            .edit()
            .remove(KEY_PENDING_SIGNATURE)
            .remove(KEY_PENDING_READY)
            .apply()
        NotificationManagerCompat.from(context).cancel(PENDING_UPDATES_NOTIFICATION_ID)
    }

    // amendment 44.2: the periodic background check also compares Krate's own version now
    // that KrateUpdateChecker exists, instead of only ever checking catalog apps. Once per version
    fun notifyKrateUpdateAvailable(
        context: Context,
        version: String,
    ) {
        val prefs = state(context)
        if (prefs.getString(KEY_KRATE_VERSION, null) == version) return
        prefs.edit().putString(KEY_KRATE_VERSION, version).apply()
        notify(
            context,
            KRATE_UPDATE_NOTIFICATION_ID,
            KrateNotification(
                headline = KrateVoice.line(Moment.KRATE_UPDATE_AVAILABLE),
                fact = "Krate $version is available. Tap to update.",
                category = "Krate update",
                // About checks on open and offers the Update button right there
                targetRoute = "settings/about",
                actionLabel = "Update now",
                largeIcon = NotificationIcons.krate(context),
            ),
        )
    }

    // after an in-app self-update Android has closed the app - this is the way back in
    fun notifyKrateUpdated(
        context: Context,
        version: String?,
    ) {
        NotificationManagerCompat.from(context).cancel(KRATE_UPDATE_NOTIFICATION_ID)
        notify(
            context,
            KRATE_UPDATED_NOTIFICATION_ID,
            KrateNotification(
                headline = KrateVoice.line(Moment.KRATE_UPDATED),
                fact = version?.let { "Krate $it is installed. Tap to open." } ?: "Krate is updated. Tap to open.",
                category = "Krate update",
                targetRoute = "home",
                actionLabel = "Open Krate",
                largeIcon = NotificationIcons.krate(context),
            ),
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
        val moment =
            when {
                failed == 0 -> Moment.INSTALLED
                succeeded == 0 -> Moment.INSTALL_FAILED
                else -> Moment.PARTLY_INSTALLED
            }
        val text =
            when {
                failed == 0 && succeeded == 1 -> "1 app updated. Everything is up to date."
                failed == 0 -> "All $succeeded apps updated. Everything is up to date."
                succeeded == 0 && failed == 1 -> "1 update failed. Tap to see what went wrong."
                succeeded == 0 -> "$failed updates failed. Tap to see what went wrong."
                else -> "$succeeded updated, $failed failed. Tap to see what went wrong."
            }
        notify(
            context,
            UPDATE_ALL_RESULT_NOTIFICATION_ID,
            KrateNotification(
                headline = KrateVoice.line(moment),
                fact = text,
                category = "Updates",
                targetRoute = "updates",
                actionLabel = if (failed == 0) "View" else "See what happened",
            ),
        )
    }

    // notification permission is optional (amendment 44.4 - onboarding does not block on it), so
    // a declined/never-granted permission means silently skipping the notification, not a failure.
    // Checked inline, not via a helper function - lint's flow analysis for NotificationManagerCompat
    // .notify() doesn't trace a permission check across a function boundary.
    private fun isShowing(
        context: Context,
        id: Int,
    ): Boolean =
        context.getSystemService(NotificationManager::class.java)?.activeNotifications?.any { it.id == id } == true

    private fun notify(
        context: Context,
        id: Int,
        message: KrateNotification,
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
                Intent(context, KrateActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_TARGET_ROUTE, message.targetRoute)
                    putExtra(EXTRA_NOTIFICATION_ID, id)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .krateContent(context, message.headline, message.fact, message.category)
                .setLargeIcon(message.largeIcon)
                .addAction(R.drawable.ic_stat_krate, message.actionLabel, openApp)
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

// what one notification says and where its tap and its button lead
private class KrateNotification(
    val headline: String,
    val fact: String,
    val category: String,
    val targetRoute: String,
    val actionLabel: String,
    val largeIcon: Bitmap? = null,
)
