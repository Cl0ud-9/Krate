package dev.cl0ud9.krate.data.downloads

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.cl0ud9.krate.EXTRA_APP_ID
import dev.cl0ud9.krate.EXTRA_NOTIFICATION_ID
import dev.cl0ud9.krate.EXTRA_TARGET_ROUTE
import dev.cl0ud9.krate.KrateActivity
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.notifications.NotificationIcons
import dev.cl0ud9.krate.platform.notifications.krateAccent
import dev.cl0ud9.krate.platform.notifications.krateContent
import dev.cl0ud9.krate.voice.KrateVoice
import dev.cl0ud9.krate.voice.Moment

// "_v2", not just "downloads": this channel originally shipped at IMPORTANCE_LOW, and Android
// permanently locks a channel's importance the first time it's created under a given id - deleting
// and recreating the SAME id does not reset that lock (confirmed live via `dumpsys notification`:
// mUserLockedFields=4/LOCKED_IMPORTANCE, mOriginalImp=2/LOW, effective importance stuck at 3/DEFAULT
// even after a delete+recreate-at-HIGH attempt). A genuinely new id is the only reliable way to
// change an already-shipped channel's importance - the old "downloads" channel is simply orphaned,
// which is harmless and the standard pattern for this exact situation
private const val CHANNEL_ID = "downloads_v2"

// one notification per download, updated in place from start to finish: live progress (the foreground service's own
// notification, so the download keeps its network in the background), then the result replacing it. Progress is
// posted at most once a second and only when it moves, and looks the same whether Krate is on screen or not.
// TooManyFunctions: one handler per download state plus their small builders, one cohesive responsibility
@Suppress("TooManyFunctions")
class AndroidDownloadProgressNotifier(
    private val context: Context,
) : DownloadProgressNotifier {
    // downloads with a live notification, and when each last posted progress (so updates stay calm)
    private val activeAppIds = mutableSetOf<String>()
    private val lastPosted = mutableMapOf<String, Pair<Long, Int>>()

    init {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shows download progress and results while the app is in the background"
            }
        notifications?.createNotificationChannel(channel)
    }

    override fun onDownloading(
        appId: String,
        appName: String,
        bytesDownloaded: Long,
        totalBytes: Long?,
    ) {
        keepAlive(appId, appName)
        val percent =
            if (totalBytes != null && totalBytes > 0) {
                ((bytesDownloaded * PERCENT_MAX) / totalBytes).toInt().coerceIn(0, PERCENT_MAX)
            } else {
                UNKNOWN_PERCENT
            }
        if (!isDue(appId, percent)) return
        // checked inline, not via a helper function - lint's flow analysis for
        // NotificationManagerCompat.notify() doesn't trace a permission check across a function
        // boundary, matching UpdateNotifier's own notify() below
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) return

        val builder = progressBuilder(appId, "Downloading $appName")
        if (percent != UNKNOWN_PERCENT) {
            val progress = "${bytesDownloaded / BYTES_PER_MB} of ${totalBytes?.div(BYTES_PER_MB)} MB · $percent%"
            builder.setContentText(progress).setProgress(PERCENT_MAX, percent, false)
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
        keepAlive(appId, appName)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) return

        val builder =
            progressBuilder(appId, "Checking $appName")
                .setContentText("Making sure the download is genuine...")
                .setProgress(0, 0, true)
        NotificationManagerCompat.from(context).notify(notificationIdFor(appId), builder.build())
    }

    override fun onComplete(app: AppProfile) {
        val result =
            terminalBuilder(
                app,
                KrateVoice.line(Moment.DOWNLOADED),
                "${app.displayName} is downloaded and checked. Tap to install.",
            ).addAction(R.drawable.ic_stat_krate, "Install", openAppIntent(app.id))
                .build()
        finish(app.id, result)
    }

    override fun onFailed(
        app: AppProfile,
        reason: String,
    ) {
        val result =
            terminalBuilder(
                app,
                KrateVoice.line(Moment.DOWNLOAD_FAILED),
                "${app.displayName} couldn't be downloaded. $reason",
            ).addAction(R.drawable.ic_stat_krate, "Try again", openAppIntent(app.id))
                .build()
        finish(app.id, result)
    }

    // cancelled, or the page went away mid-download: the notification goes with it
    override fun clear(appId: String) {
        if (appId in activeAppIds) finish(appId, result = null)
    }

    // the result takes the progress notification's place when Krate is in the background; on screen, the page
    // already says it, so the notification just goes
    private fun finish(
        appId: String,
        result: Notification?,
    ) {
        activeAppIds -= appId
        lastPosted -= appId
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        val shown = result?.takeIf { granted == PackageManager.PERMISSION_GRANTED && !isAppInForeground() }
        if (DownloadKeepAlive.release(appId, shown)) return
        if (shown != null && granted == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(notificationIdFor(appId), shown)
        } else {
            NotificationManagerCompat.from(context).cancel(notificationIdFor(appId))
        }
    }

    override fun onUpdateAllStarted() {
        val notification =
            progressBuilder(UPDATE_ALL_KEY, "Updating your apps")
                .setContentText("Keeps going if you switch apps.")
                .setProgress(0, 0, true)
                .build()
        DownloadKeepAlive.hold(context, UPDATE_ALL_KEY, UPDATE_ALL_NOTIFICATION_ID, notification)
    }

    override fun onUpdateAllFinished() {
        if (!DownloadKeepAlive.release(
                UPDATE_ALL_KEY,
            )
        ) {
            NotificationManagerCompat.from(context).cancel(UPDATE_ALL_NOTIFICATION_ID)
        }
    }

    // the first word of a download keeps Krate's network through a foreground service until it ends
    private fun keepAlive(
        appId: String,
        appName: String,
    ) {
        if (!activeAppIds.add(appId)) return
        val starting =
            progressBuilder(appId, "Downloading $appName")
                .setContentText("Starting...")
                .setProgress(0, 0, true)
                .build()
        DownloadKeepAlive.hold(context, appId, notificationIdFor(appId), starting)
    }

    // at most one progress update a second, and only when the number moved
    private fun isDue(
        appId: String,
        percent: Int,
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        val last = lastPosted[appId]
        val due = last == null || (percent != last.second && now - last.first >= MIN_UPDATE_GAP_MS)
        if (due) lastPosted[appId] = now to percent
        return due
    }

    private fun progressBuilder(
        appId: String,
        title: String,
    ): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            // the system's own animated download arrow - a status-bar icon has to be a single-color
            // silhouette, and the full-color launcher icon rendered as a blank circle
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(krateAccent(context))
            .setSubText("Downloads")
            .setContentTitle(title)
            .setContentIntent(openAppIntent(appId))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // a quick download finishes before this ever shows
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)

    // dismissible (not ongoing) and NOT silent, unlike the in-progress builder above - this is a
    // one-shot result the user should actually notice, not a running-task indicator. The app's own
    // icon as the large icon says which app it's about
    private fun terminalBuilder(
        app: AppProfile,
        headline: String,
        fact: String,
    ): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .krateContent(context, headline, fact, "Downloads")
            .setLargeIcon(NotificationIcons.app(context, app))
            .setContentIntent(openAppIntent(app.id))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_EVENT)

    private fun openAppIntent(appId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            appId.hashCode(),
            Intent(context, KrateActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_ROUTE, "apps/$appId")
                putExtra(EXTRA_APP_ID, appId)
                // a finished download's result is dismissed when it opens Krate; a running one's progress can't be
                putExtra(EXTRA_NOTIFICATION_ID, notificationIdFor(appId))
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner
            .get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)

    // one per app, so two downloads at once (a manual one plus Update All) never overwrite each other
    private fun notificationIdFor(appId: String): Int = NOTIFICATION_ID_BASE + appId.hashCode()

    private companion object {
        const val PERCENT_MAX = 100
        const val UNKNOWN_PERCENT = -1
        const val MIN_UPDATE_GAP_MS = 1_000L
        const val BYTES_PER_MB = 1024L * 1024L
        const val NOTIFICATION_ID_BASE = 2000
        const val UPDATE_ALL_KEY = "update-all"
        const val UPDATE_ALL_NOTIFICATION_ID = 1999
    }
}
