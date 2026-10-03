package dev.cl0ud9.krate.platform.autoupdate

import android.content.Context

private const val LEDGER_PREFS = "auto_update_ledger"
private const val SEPARATOR = '\u001f'

// how long a handed-over install may wait for its app to be free before Krate tries it again
internal const val AUTO_UPDATE_WAIT_MILLIS = 3L * 60 * 60 * 1000
private const val IN_FLIGHT_STALE_MILLIS = AUTO_UPDATE_WAIT_MILLIS + 60L * 60 * 1000

// what Krate has tried in the background: failed tries per build, and installs Android is still holding
internal class AutoUpdateLedger(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(LEDGER_PREFS, Context.MODE_PRIVATE)

    // only the newest build's count is kept; a new build starts again from nothing
    fun tries(
        appId: String,
        buildKey: String,
    ): Int {
        val entry = entry("tries:$appId")
        return if (entry.firstOrNull() == buildKey) entry.getOrNull(1)?.toIntOrNull() ?: 0 else 0
    }

    fun failed(
        appId: String,
        buildKey: String,
    ) = setTries(appId, buildKey, tries(appId, buildKey) + 1)

    // never tried again by itself; the user installs it
    fun giveUp(
        appId: String,
        buildKey: String,
    ) = setTries(appId, buildKey, MAX_AUTO_UPDATE_TRIES)

    fun isInFlight(
        appId: String,
        buildKey: String,
        nowMillis: Long,
    ): Boolean {
        val entry = entry("flight:$appId")
        val started = entry.getOrNull(1)?.toLongOrNull() ?: 0L
        return entry.firstOrNull() == buildKey && nowMillis - started < IN_FLIGHT_STALE_MILLIS
    }

    fun handedOver(
        appId: String,
        buildKey: String,
        nowMillis: Long,
    ) = prefs.edit().putString("flight:$appId", "$buildKey$SEPARATOR$nowMillis").apply()

    fun landed(appId: String) = prefs.edit().remove("flight:$appId").apply()

    // when Krate first found this build due, noted the first time it asks
    fun dueSince(
        appId: String,
        buildKey: String,
        nowMillis: Long,
    ): Long {
        val entry = entry("due:$appId")
        val noted = entry.getOrNull(1)?.toLongOrNull()?.takeIf { entry.firstOrNull() == buildKey }
        if (noted == null) prefs.edit().putString("due:$appId", "$buildKey$SEPARATOR$nowMillis").apply()
        return noted ?: nowMillis
    }

    private fun entry(key: String): List<String> = prefs.getString(key, null)?.split(SEPARATOR).orEmpty()

    private fun setTries(
        appId: String,
        buildKey: String,
        count: Int,
    ) = prefs.edit().putString("tries:$appId", "$buildKey$SEPARATOR$count").apply()
}
