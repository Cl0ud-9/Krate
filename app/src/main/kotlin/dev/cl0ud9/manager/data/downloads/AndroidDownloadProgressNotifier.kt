package dev.cl0ud9.manager.data.downloads

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import dev.cl0ud9.manager.EXTRA_APP_ID
import dev.cl0ud9.manager.EXTRA_TARGET_ROUTE
import dev.cl0ud9.manager.MainActivity
import dev.cl0ud9.manager.R

// "_v2", not just "downloads": this channel originally shipped at IMPORTANCE_LOW, and Android
// permanently locks a channel's importance the first time it's created under a given id - deleting
// and recreating the SAME id does not reset that lock (confirmed live via `dumpsys notification`:
// mUserLockedFields=4/LOCKED_IMPORTANCE, mOriginalImp=2/LOW, effective importance stuck at 3/DEFAULT
// even after a delete+recreate-at-HIGH attempt). A genuinely new id is the only reliable way to
// change an already-shipped channel's importance - the old "downloads" channel is simply orphaned,
// which is harmless and the standard pattern for this exact situation
private const val CHANNEL_ID = "downloads_v2"

// only ever posts progress while the whole app process is backgrounded - App Details already
// shows this same progress on screen while the app is in the foreground, so notifying there too
// would just be a redundant, noisy duplicate of what the user is already looking at. A terminal
// complete/failed result is the one exception: it's posted whenever the app was backgrounded at
// the moment it happened, and then left alone (not swept away by simply reopening the app),
// exactly like a normal download manager's notification behaves. TooManyFunctions is a real but
// justified count: one state (downloading/verifying/complete/failed/clear) per DownloadStatus
// variant plus its own small builder/permission/id helpers, all inherent to one cohesive
// responsibility (this notifier), not something splitting into more classes would shrink
@Suppress("TooManyFunctions")
class AndroidDownloadProgressNotifier(
    private val context: Context,
) : DownloadProgressNotifier {
    // ids with an ACTIVE in-progress notification showing, so returning to the app can dismiss
    // just those immediately instead of waiting for each download's next progress tick (which, on
    // a slow connection, could be seconds away) - a completed/failed terminal notification is
    // deliberately never added here, so it isn't swept away by the same mechanism
    private val activeAppIds = mutableSetOf<String>()

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shows download progress and results while the app is in the background"
            }
        manager?.createNotificationChannel(channel)

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    activeAppIds.toList().forEach(::clear)
                }
            },
        )
    }

    override fun onDownloading(
        appId: String,
        appName: String,
        bytesDownloaded: Long,
        totalBytes: Long?,
    ) {
        if (isAppInForeground()) {
            clear(appId)
            return
        }
        // checked inline, not via a helper function - lint's flow analysis for
        // NotificationManagerCompat.notify() doesn't trace a permission check across a function
        // boundary, matching UpdateNotifier's own notify() below
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) return

        activeAppIds += appId
        val builder = progressBuilder(appId, "Downloading $appName")
        if (totalBytes != null && totalBytes > 0) {
            val percent = ((bytesDownloaded * PERCENT_MAX) / totalBytes).toInt().coerceIn(0, PERCENT_MAX)
            builder.setContentText("$percent%").setProgress(PERCENT_MAX, percent, false)
        } else {
            // total size unknown (server didn't report Content-Length) - shown as an indeterminate
            // bar with the raw byte count instead of a fabricated percentage
            builder.setContentText("${bytesDownloaded / BYTES_PER_MB}MB downloaded").setProgress(0, 0, true)
        }
        NotificationManagerCompat.from(context).notify(notificationIdFor(appId), builder.build())
    }

    override fun onVerifying(
        appId: String,
        appName: String,
    ) {
        if (isAppInForeground()) {
            clear(appId)
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) return

        activeAppIds += appId
        val builder =
            progressBuilder(appId, "Verifying $appName")
                .setContentText("Checking the download...")
                .setProgress(0, 0, true)
        NotificationManagerCompat.from(context).notify(notificationIdFor(appId), builder.build())
    }

    // posted even if the app has since come back to the foreground, as long as it was backgrounded
    // at the actual moment the download finished - if the app was in the foreground the whole time,
    // the live UI already showed this, so a notification on top of that would be pure noise
    override fun onComplete(
        appId: String,
        appName: String,
    ) {
        activeAppIds -= appId
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (isAppInForeground() || granted != PackageManager.PERMISSION_GRANTED) return
        val notification =
            terminalBuilder(appId, "$appName downloaded")
                .setContentText("Ready to install. Tap to open.")
                .build()
        NotificationManagerCompat.from(context).notify(notificationIdFor(appId), notification)
    }

    override fun onFailed(
        appId: String,
        appName: String,
        reason: String,
    ) {
        activeAppIds -= appId
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (isAppInForeground() || granted != PackageManager.PERMISSION_GRANTED) return
        val notification =
            terminalBuilder(appId, "$appName download failed")
                .setContentText(reason)
                .build()
        NotificationManagerCompat.from(context).notify(notificationIdFor(appId), notification)
    }

    override fun clear(appId: String) {
        activeAppIds -= appId
        NotificationManagerCompat.from(context).cancel(notificationIdFor(appId))
    }

    private fun progressBuilder(
        appId: String,
        title: String,
    ): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentIntent(openAppIntent(appId))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    // dismissible (not ongoing) and NOT silent, unlike the in-progress builder above - this is a
    // one-shot result the user should actually notice, not a running-task indicator
    private fun terminalBuilder(
        appId: String,
        title: String,
    ): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentIntent(openAppIntent(appId))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_EVENT)

    private fun openAppIntent(appId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            appId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_ROUTE, "apps/$appId")
                putExtra(EXTRA_APP_ID, appId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner
            .get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)

    // spread out per app id so two concurrent downloads (a manual one plus Update All, or two
    // manual downloads in sequence before the first notification is dismissed) get distinct
    // notifications instead of overwriting one another
    private fun notificationIdFor(appId: String): Int = NOTIFICATION_ID_BASE + appId.hashCode()

    private companion object {
        const val PERCENT_MAX = 100
        const val BYTES_PER_MB = 1024L * 1024L
        const val NOTIFICATION_ID_BASE = 2000
    }
}
