package dev.cl0ud9.krate.platform.suggest

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.cl0ud9.krate.domain.suggest.SuggestionMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val PREFS = "krate_suggestions"
private const val KEY_ENABLED = "enabled"
private const val KEY_TURNED_DOWN = "turned_down"
private const val KEY_LOOKED = "looked"
private const val KEY_SEEN = "seen"
private const val KEY_SEEN_SET = "seen_noted"

// what Krate remembers about its own suggestions, kept only on this phone: whether they're on, the apps turned down,
// the ones looked at and when, and which apps were on the shelf when suggestions began, so only later arrivals count as
// new. Nothing here leaves the phone, and it goes if Krate is uninstalled
object SuggestionStore {
    private var prefs: SharedPreferences? = null
    private val mutableMemory = MutableStateFlow(SuggestionMemory())
    val memory: StateFlow<SuggestionMemory> = mutableMemory.asStateFlow()

    fun load(context: Context) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        mutableMemory.value =
            SuggestionMemory(
                enabled = store.getBoolean(KEY_ENABLED, true),
                turnedDown = store.getStringSet(KEY_TURNED_DOWN, emptySet()).orEmpty().toSet(),
                lookedAt =
                    store
                        .getStringSet(KEY_LOOKED, emptySet())
                        .orEmpty()
                        .mapNotNull(::readLook)
                        .toMap(),
                seen =
                    store
                        .getStringSet(
                            KEY_SEEN,
                            emptySet(),
                        ).orEmpty()
                        .takeIf { store.getBoolean(KEY_SEEN_SET, false) },
            )
    }

    fun setEnabled(enabled: Boolean) {
        prefs?.edit { putBoolean(KEY_ENABLED, enabled) }
        mutableMemory.update { it.copy(enabled = enabled) }
    }

    // "Not for me": never put forward again
    fun turnDown(appId: String) {
        val down = mutableMemory.value.turnedDown + appId
        prefs?.edit { putStringSet(KEY_TURNED_DOWN, down) }
        mutableMemory.update { it.copy(turnedDown = down, seen = it.seen?.plus(appId)) }
        persistSeen()
    }

    // opened its page from a suggestion: not mentioned again for a while, and no longer new
    fun looked(
        appId: String,
        nowMillis: Long,
    ) {
        val looks = mutableMemory.value.lookedAt + (appId to nowMillis)
        prefs?.edit { putStringSet(KEY_LOOKED, looks.map { (id, at) -> "$id|$at" }.toSet()) }
        mutableMemory.update { it.copy(lookedAt = looks, seen = it.seen?.plus(appId)) }
        persistSeen()
    }

    // an arrival acknowledged without opening it
    fun markSeen(appId: String) {
        mutableMemory.update { it.copy(seen = it.seen?.plus(appId)) }
        persistSeen()
    }

    // what's on the shelf the first time suggestions run: all of it counts as seen, so nothing already there is
    // announced as new. Later calls do nothing
    fun noteShelf(appIds: Set<String>) {
        if (appIds.isEmpty() || mutableMemory.value.seen != null) return
        mutableMemory.update { it.copy(seen = appIds) }
        persistSeen()
    }

    // apps that went on the phone are no longer arrivals to announce
    fun noteInstalled(appIds: Set<String>) {
        val seen = mutableMemory.value.seen ?: return
        if (seen.containsAll(appIds)) return
        mutableMemory.update { it.copy(seen = seen + appIds) }
        persistSeen()
    }

    private fun persistSeen() {
        val seen = mutableMemory.value.seen ?: return
        prefs?.edit {
            putStringSet(KEY_SEEN, seen)
            putBoolean(KEY_SEEN_SET, true)
        }
    }

    private fun readLook(entry: String): Pair<String, Long>? {
        val parts = entry.split('|')
        val at = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return parts[0] to at
    }
}
