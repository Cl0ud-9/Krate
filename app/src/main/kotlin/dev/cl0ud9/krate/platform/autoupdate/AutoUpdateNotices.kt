package dev.cl0ud9.krate.platform.autoupdate

import android.content.Context
import dev.cl0ud9.krate.platform.workers.KrateNotification
import dev.cl0ud9.krate.platform.workers.UpdateNotifier
import dev.cl0ud9.krate.voice.KrateVoice
import dev.cl0ud9.krate.voice.Moment

private const val AUTO_UPDATED_NOTIFICATION_ID = 1005
private const val AUTO_UPDATE_NEEDS_YOU_NOTIFICATION_ID = 1006
private const val NOTICE_PREFS = "auto_update_notices"
private const val NAME_SEPARATOR = '\u001f'

// one notification per kind that grows while it's still showing, so a night of updates doesn't buzz once per app
internal object AutoUpdateNotices {
    fun updated(
        context: Context,
        appName: String,
    ) {
        val names = gather(context, AUTO_UPDATED_NOTIFICATION_ID, appName)
        UpdateNotifier.notify(
            context,
            AUTO_UPDATED_NOTIFICATION_ID,
            KrateNotification(
                headline = KrateVoice.line(Moment.INSTALLED),
                fact = updatedText(names),
                category = "Updated automatically",
                targetRoute = "home",
                actionLabel = "View",
            ),
        )
    }

    // installs Krate couldn't finish by itself; the download is still there for one tap in the app
    fun needsYou(
        context: Context,
        appName: String,
    ) {
        val names = gather(context, AUTO_UPDATE_NEEDS_YOU_NOTIFICATION_ID, appName)
        UpdateNotifier.notify(
            context,
            AUTO_UPDATE_NEEDS_YOU_NOTIFICATION_ID,
            KrateNotification(
                headline = KrateVoice.line(Moment.UPDATES_WAITING),
                fact = needsYouText(names),
                category = "Updates",
                targetRoute = "updates",
                actionLabel = "Review",
            ),
        )
    }

    // the names already on a notification that's still showing, plus this one
    private fun gather(
        context: Context,
        id: Int,
        appName: String,
    ): List<String> {
        val prefs = context.getSharedPreferences(NOTICE_PREFS, Context.MODE_PRIVATE)
        val shown =
            if (UpdateNotifier.isShowing(context, id)) {
                prefs.getString("names:$id", null)?.split(NAME_SEPARATOR).orEmpty()
            } else {
                emptyList()
            }
        val names = (shown - appName) + appName
        prefs.edit().putString("names:$id", names.joinToString(NAME_SEPARATOR.toString())).apply()
        return names
    }
}

internal fun updatedText(names: List<String>): String =
    names.singleOrNull()?.let { "Krate updated $it while you weren't using it." }
        ?: "Krate updated ${names.joinToString(", ")} while you weren't using them."

internal fun needsYouText(names: List<String>): String =
    names.singleOrNull()?.let { "$it's update is downloaded and needs you to install it. Tap to review." }
        ?: "Updates for ${names.joinToString(", ")} are downloaded and need you to install them. Tap to review."
