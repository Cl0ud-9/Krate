package dev.cl0ud9.krate.data.downloads

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

// Android cuts a backgrounded app off the network within seconds, so a download the user started would fail the
// moment they switch apps. While one is running, this service keeps Krate in the foreground-service state that
// keeps its network, showing that download's own progress notification, and it stops the moment the last one ends
class DownloadKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // always entered, even when the download already ended: a requested foreground service that never
        // calls startForeground gets the app killed
        val (id, notification) = DownloadKeepAlive.shown() ?: return stopNow()
        ServiceCompat.startForeground(this, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        DownloadKeepAlive.running = true
        if (!DownloadKeepAlive.anyHeld()) stopNow()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        DownloadKeepAlive.running = false
        super.onDestroy()
    }

    private fun stopNow(): Int {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }
}

// which downloads are holding the service up; the newest one's notification is the one it shows
object DownloadKeepAlive {
    private val held = linkedMapOf<String, Pair<Int, Notification>>()
    private var lastShown: Pair<Int, Notification>? = null

    @Volatile
    internal var running = false

    // call from the tap that starts the download, while Krate is still on screen
    @Synchronized
    fun hold(
        context: Context,
        key: String,
        notificationId: Int,
        notification: Notification,
    ) {
        held[key] = notificationId to notification
        lastShown = notificationId to notification
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, DownloadKeepAliveService::class.java))
        }
    }

    @Synchronized
    fun isHeld(key: String): Boolean = key in held

    // a service that hasn't started yet sees nothing held and stops itself, so it's only stopped from here once running
    @Synchronized
    fun release(
        context: Context,
        key: String,
    ) {
        if (held.remove(key) != null && held.isEmpty() && running) {
            runCatching { context.stopService(Intent(context, DownloadKeepAliveService::class.java)) }
        }
    }

    @Synchronized
    internal fun anyHeld(): Boolean = held.isNotEmpty()

    @Synchronized
    internal fun shown(): Pair<Int, Notification>? = held.values.lastOrNull() ?: lastShown
}
