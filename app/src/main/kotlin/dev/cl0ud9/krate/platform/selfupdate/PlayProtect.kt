package dev.cl0ud9.krate.platform.selfupdate

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.PackageInfoCompat

// Google's Play Protect settings, where "Scan apps with Play Protect" is switched off and on
private const val VERIFY_APPS_SETTINGS = "com.google.android.gms.settings.VERIFY_APPS_SETTINGS"

// Play Protect also lives in the Play Store's menu, for when that settings screen can't be opened
private const val PLAY_STORE = "com.android.vending"

private const val PREFS = "play_protect"
private const val KEY_PAUSED_FROM_VERSION = "paused_from_version"
private const val KEY_PAUSED_AT = "paused_at"

// an update not finished within this long was given up on, and scanning should still go back on
private const val GIVEN_UP_AFTER_MILLIS = 60L * 60 * 1000

// a notification route that opens Play Protect instead of a screen in Krate
const val PLAY_PROTECT_ROUTE = "play-protect"

// Krate can't switch Play Protect itself, only take the user to it, and remember to bring them back once its own
// update is in, so scanning is never left off by accident
object PlayProtect {
    // false only when neither Google's settings nor the Play Store could be opened
    fun open(context: Context): Boolean {
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
            .putLong(KEY_PAUSED_FROM_VERSION, versionCode(context))
            .putLong(KEY_PAUSED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clearReminder(context: Context) {
        prefs(context).edit().clear().apply()
    }

    // scanning was paused for an update, whether or not the update has gone on yet
    fun reminderPending(context: Context): Boolean = prefs(context).contains(KEY_PAUSED_FROM_VERSION)

    // time to ask: Krate has updated past the version it was paused from, or the update was given up on
    fun reminderDue(context: Context): Boolean {
        val prefs = prefs(context)
        val pausedFrom = prefs.getLong(KEY_PAUSED_FROM_VERSION, -1L)
        val pausedAt = prefs.getLong(KEY_PAUSED_AT, 0L)
        return pausedFrom >= 0 &&
            (versionCode(context) > pausedFrom || System.currentTimeMillis() - pausedAt > GIVEN_UP_AFTER_MILLIS)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun versionCode(context: Context): Long =
        runCatching {
            PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        }.getOrDefault(0L)
}
