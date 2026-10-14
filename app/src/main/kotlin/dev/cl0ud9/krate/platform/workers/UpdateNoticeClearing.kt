package dev.cl0ud9.krate.platform.workers

import android.content.Context
import androidx.core.app.NotificationManagerCompat

// taking update notifications down once what they announced is in front of the person: they stay in the shade
// otherwise, and a tap on a notice that has nothing left to say is a tap that seems to do nothing
object UpdateNoticeClearing {
    // the person is looking at the Updates screen, which says everything these were for; the notices go, and what they
    // announced isn't raised again
    fun clearUpdateNotices(context: Context) {
        val notifications = NotificationManagerCompat.from(context)
        notifications.cancel(PENDING_UPDATES_NOTIFICATION_ID)
        notifications.cancel(UPDATE_ALL_RESULT_NOTIFICATION_ID)
        notifications.cancel(AUTO_UPDATE_NEEDS_YOU_NOTIFICATION_ID)
    }

    // the page that offers the update is open
    fun clearKrateUpdateNotice(context: Context) {
        NotificationManagerCompat.from(context).cancel(KRATE_UPDATE_NOTIFICATION_ID)
    }

    // Home's own activity list says what was updated while they were away
    fun clearAutoUpdatedNotice(context: Context) {
        NotificationManagerCompat.from(context).cancel(AUTO_UPDATED_NOTIFICATION_ID)
    }

    // the update is on screen (Home's dialog, or the About page), so its notification has nothing left to say, and
    // the background check mustn't raise one afterwards
    fun krateUpdateSeen(
        context: Context,
        version: String,
    ) {
        context
            .getSharedPreferences(
                STATE_PREFS,
                Context.MODE_PRIVATE,
            ).edit()
            .putString(KEY_KRATE_VERSION, version)
            .apply()
        NotificationManagerCompat.from(context).cancel(KRATE_UPDATE_NOTIFICATION_ID)
    }
}
