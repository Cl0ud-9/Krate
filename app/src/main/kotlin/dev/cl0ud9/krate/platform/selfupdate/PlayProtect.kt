package dev.cl0ud9.krate.platform.selfupdate

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.pm.PackageInfoCompat

// Google's Play Protect settings, where "Scan apps with Play Protect" is switched off and on
private const val VERIFY_APPS_SETTINGS = "com.google.android.gms.settings.VERIFY_APPS_SETTINGS"

// Play Protect also lives in the Play Store's menu, for when that settings screen can't be opened
private const val PLAY_STORE = "com.android.vending"

private const val PREFS = "play_protect"
private const val KEY_PAUSED_FROM_VERSION = "paused_from_version"
private const val KEY_PAUSED_AT = "paused_at"
private const val KEY_PAUSED_FOR_APP = "paused_for_app"
private const val KEY_PAUSED_FOR_PACKAGE = "paused_for_package"
private const val KEY_APP_INSTALLED = "app_installed"

private const val FLAGS = "play_protect_flags"

// an update not finished within this long was given up on, and scanning should still go back on
private const val GIVEN_UP_AFTER_MILLIS = 60L * 60 * 1000

// an app install takes a minute, not an hour: past this, the reminder comes whether or not it went on
internal const val APP_GIVEN_UP_AFTER_MILLIS = 15L * 60 * 1000

// a notification route that opens Play Protect instead of a screen in Krate
const val PLAY_PROTECT_ROUTE = "play-protect"

// Krate can't switch Play Protect itself, only take the user to it, and remember to bring them back once its own
// update, or the app it was paused for, is in, so scanning is never left off by accident
object PlayProtect {
    // bumped on every change, so screens showing a reminder or a heads-up redraw at once
    val changes = mutableIntStateOf(0)

    // false only when neither Google's settings nor the Play Store could be opened
    fun open(context: Context): Boolean {
        // Play Protect opens in the Play Store's own task whatever Krate asks, so Back stays there; people come back
        // to Krate from Recents, where the guide is waiting on its next step
        val settings = Intent(VERIFY_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val store =
            context.packageManager
                .getLaunchIntentForPackage(
                    PLAY_STORE,
                )?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return listOfNotNull(settings, store).any { runCatching { context.startActivity(it) }.isSuccess }
    }

    // the user went to pause scanning for Krate's update
    fun notePaused(context: Context) {
        prefs(context)
            .edit()
            .clear()
            .putLong(KEY_PAUSED_FROM_VERSION, versionCode(context))
            .putLong(KEY_PAUSED_AT, System.currentTimeMillis())
            .apply()
        changes.intValue++
    }

    // the user went to pause scanning to install one app
    fun notePausedFor(
        context: Context,
        appName: String,
        packageName: String,
    ) {
        prefs(context)
            .edit()
            .clear()
            .putString(KEY_PAUSED_FOR_APP, appName)
            .putString(KEY_PAUSED_FOR_PACKAGE, packageName)
            .putLong(KEY_PAUSED_AT, System.currentTimeMillis())
            .apply()
        changes.intValue++
    }

    // an app went on: if scanning was paused for it, it's time to turn it back on
    fun noteInstalled(
        context: Context,
        packageName: String,
    ) {
        val prefs = prefs(context)
        if (prefs.getString(KEY_PAUSED_FOR_PACKAGE, null) ==
            packageName
        ) {
            prefs.edit().putBoolean(KEY_APP_INSTALLED, true).apply()
        }
    }

    // the app scanning was paused for, by name; null when it was paused for Krate's own update, or not at all
    fun pausedForApp(context: Context): String? = prefs(context).getString(KEY_PAUSED_FOR_APP, null)

    fun pausedForPackage(context: Context): String? = prefs(context).getString(KEY_PAUSED_FOR_PACKAGE, null)

    fun clearReminder(context: Context) {
        prefs(context).edit().clear().apply()
        changes.intValue++
    }

    // scanning was paused for an update or an app, whether or not it has gone on yet
    fun reminderPending(context: Context): Boolean =
        prefs(context).let { it.contains(KEY_PAUSED_FROM_VERSION) || it.contains(KEY_PAUSED_FOR_PACKAGE) }

    // time to ask: what it was paused for is in, or was given up on
    fun reminderDue(context: Context): Boolean {
        val prefs = prefs(context)
        val pausedAt = prefs.getLong(KEY_PAUSED_AT, 0L)
        val since = System.currentTimeMillis() - pausedAt
        val pausedFrom = prefs.getLong(KEY_PAUSED_FROM_VERSION, -1L)
        val forKrate = pausedFrom >= 0 && (versionCode(context) > pausedFrom || since > GIVEN_UP_AFTER_MILLIS)
        val forApp =
            prefs.contains(KEY_PAUSED_FOR_PACKAGE) &&
                (prefs.getBoolean(KEY_APP_INSTALLED, false) || since > APP_GIVEN_UP_AFTER_MILLIS)
        return forKrate || forApp
    }
}

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private fun versionCode(context: Context): Long =
    runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)

// the apps Play Protect has stopped, kept apart from the reminder so settling a reminder never forgets them
object PlayProtectFlags {
    // Play Protect stopped this app, so its next install starts with the way past it, and it doesn't update by itself
    fun flag(
        context: Context,
        packageName: String,
    ) {
        flags(context).edit().putLong(packageName, System.currentTimeMillis()).apply()
        PlayProtect.changes.intValue++
    }

    fun isFlagged(
        context: Context,
        packageName: String,
    ): Boolean = flags(context).contains(packageName)

    // it went on with scanning switched on: Play Protect has let it through, so it's an ordinary app again
    fun unflag(
        context: Context,
        packageName: String,
    ) {
        if (!isFlagged(context, packageName)) return
        flags(context).edit().remove(packageName).apply()
        PlayProtect.changes.intValue++
    }

    private fun flags(context: Context) = context.getSharedPreferences(FLAGS, Context.MODE_PRIVATE)
}

// what Play Protect did to an install, from wherever it ran: stopped it, so the app is flagged, or let it through
fun notePlayProtect(
    context: Context,
    packageName: String,
    stopped: Boolean,
) {
    when {
        stopped -> PlayProtectFlags.flag(context, packageName)
        // scanning was paused for it: time to ask for it back on
        PlayProtect.pausedForPackage(context) == packageName -> PlayProtect.noteInstalled(context, packageName)
        // it went on with scanning switched on, so Play Protect has come round to it
        else -> PlayProtectFlags.unflag(context, packageName)
    }
}
