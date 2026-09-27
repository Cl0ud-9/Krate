package dev.cl0ud9.krate.data.downloads

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

// only ever shown for the instant a service that started too late needs before stopping again
private const val PLACEHOLDER_NOTIFICATION_ID = 1998

// Android cuts a backgrounded app off the network within seconds, so a download the user started would fail the
// moment they switch apps. While one is running, this service keeps Krate in the foreground-service state that
// keeps its network, showing that download's own progress notification, and it stops the moment the last one ends
class DownloadKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val current = DownloadKeepAlive.current()
        if (current != null) {
            show(current.first, current.second)
        } else {
            // the download already ended: a requested foreground service still has to call startForeground or
            // the app is killed, so it does, under an id no download uses, and goes straight away
            DownloadKeepAlive.lastShown()?.let { show(PLACEHOLDER_NOTIFICATION_ID, it) }
            stop(keepNotification = false)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (running === this) running = null
        super.onDestroy()
    }

    internal fun show(
        id: Int,
        notification: Notification,
    ) {
        ServiceCompat.startForeground(this, id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    // kept, it stays up as an ordinary notification showing whatever the service last showed; otherwise it goes
    internal fun stop(keepNotification: Boolean) {
        val mode = if (keepNotification) ServiceCompat.STOP_FOREGROUND_DETACH else ServiceCompat.STOP_FOREGROUND_REMOVE
        ServiceCompat.stopForeground(this, mode)
        stopSelf()
    }

    internal companion object {
        @Volatile
        var running: DownloadKeepAliveService? = null
    }
}

// which downloads are holding the service up; the newest one's notification is the one it shows. Called on the
// main thread, like the service itself
object DownloadKeepAlive {
    private val held = linkedMapOf<String, Pair<Int, Notification>>()
    private var lastShown: Notification? = null

    // call from the tap that starts the download, while Krate is still on screen
    @Synchronized
    fun hold(
        context: Context,
        key: String,
        notificationId: Int,
        notification: Notification,
    ) {
        held[key] = notificationId to notification
        lastShown = notification
        val service = DownloadKeepAliveService.running
        if (service != null) {
            service.show(notificationId, notification)
        } else {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, DownloadKeepAliveService::class.java))
            }
        }
    }

    @Synchronized
    fun isHeld(key: String): Boolean = key in held

    // ends a download's hold. When it was the one the service shows, the service itself ends its notification,
    // swapping in [result] before letting go (so Android can't put the old progress back over it) or removing it.
    // Returns false when the caller still has to post [result] or cancel the notification itself
    @Synchronized
    fun release(
        key: String,
        result: Notification? = null,
    ): Boolean {
        val entry = held.remove(key)
        val service = DownloadKeepAliveService.running
        val next = held.values.lastOrNull()
        return when {
            entry == null || service == null -> false
            next != null -> {
                service.show(next.first, next.second)
                false
            }
            else -> {
                if (result != null) service.show(entry.first, result)
                service.stop(keepNotification = result != null)
                true
            }
        }
    }

    @Synchronized
    internal fun current(): Pair<Int, Notification>? = held.values.lastOrNull()

    @Synchronized
    internal fun lastShown(): Notification? = lastShown
}
