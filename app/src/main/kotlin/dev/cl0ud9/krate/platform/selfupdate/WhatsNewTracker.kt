package dev.cl0ud9.krate.platform.selfupdate

import android.content.Context
import dev.cl0ud9.krate.platform.workers.UpdateNoticeClearing

private const val PREFS = "whats_new"
private const val KEY_LAST_SEEN = "last_seen_version"
private const val KEY_ANNOUNCED = "announced_update"

// remembers which Krate version the user last opened, so the first open after an update can say what changed
class WhatsNewTracker(
    private val context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    val currentVersion: String? by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    // the version just updated to, once - null on a fresh install, an unchanged version, or every later call
    fun takeJustUpdatedVersion(): String? {
        val current = currentVersion
        val lastSeen = prefs.getString(KEY_LAST_SEEN, null)
        if (current == null || lastSeen == current) return null
        prefs.edit().putString(KEY_LAST_SEEN, current).apply()
        // nothing stored yet but installed over an older version: an update from before this was tracked
        return current.takeIf { lastSeen != null || installedOverOlderVersion() }
    }

    // the dialog about a newer Krate is made once per version, not at every launch until it's installed; the other
    // places that offer it (Settings, the notification) stay
    fun announcementDue(version: String): Boolean = prefs.getString(KEY_ANNOUNCED, null) != version

    // the dialog is on screen: its notification stands down, and the background check won't raise one for it later
    fun announcementShown(version: String) {
        UpdateNoticeClearing.krateUpdateSeen(context, version)
    }

    // the dialog has had its say: no repeat for this version
    fun announcementDone(version: String) {
        prefs.edit().putString(KEY_ANNOUNCED, version).apply()
    }

    private fun installedOverOlderVersion(): Boolean =
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.lastUpdateTime > info.firstInstallTime
        }.getOrDefault(false)
}
