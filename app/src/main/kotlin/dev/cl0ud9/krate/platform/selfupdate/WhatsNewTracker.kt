package dev.cl0ud9.krate.platform.selfupdate

import android.content.Context

private const val PREFS = "whats_new"
private const val KEY_LAST_SEEN = "last_seen_version"

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

    private fun installedOverOlderVersion(): Boolean =
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.lastUpdateTime > info.firstInstallTime
        }.getOrDefault(false)
}
