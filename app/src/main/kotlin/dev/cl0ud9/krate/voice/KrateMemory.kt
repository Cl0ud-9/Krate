package dev.cl0ud9.krate.voice

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import dev.cl0ud9.krate.domain.model.ActivityAction

private const val PREFS = "krate_memory"
private const val KEY_INSTALLS = "installs"
private const val KEY_UPDATES = "updates"
private const val KEY_VERIFIED = "verified"
private const val KEY_FINDS = "finds"
private const val KEY_MILESTONE = "milestone"
private const val KEY_CAUGHT_UP_SINCE = "caught_up_since"
private const val KEY_ADD_HINT = "add_hint_done"
private const val UPDATES_OF = "updates_of_"
internal const val DAY_MS = 24L * 60 * 60 * 1000

// the counts that hit a milestone, and what Krate says when they do
private val INSTALL_MILESTONES =
    mapOf(
        1 to "Your first app from the Krate.",
        5 to "Five apps in. Nice start.",
        10 to "Ten apps in. It's a collection now.",
        25 to "25 apps. That's a proper shelf.",
        50 to "50 apps. Krate is impressed.",
    )
private val UPDATE_MILESTONES =
    mapOf(
        10 to "Ten updates in, all checked.",
        50 to "Your 50th update with Krate.",
        100 to "100 updates. Krate is proud.",
        250 to "250 updates and counting.",
        500 to "500 updates. A true Krate keeper.",
    )

// what Krate remembers about you, kept only on this phone: how many apps and updates it has brought, how many
// downloads it has checked, the milestone it owes you, and how long you've been all caught up. Nothing here leaves
// the phone, and it all goes if Krate is uninstalled
object KrateMemory {
    // shared with KrateWeek, which keeps the week's events in the same place
    internal var prefs: SharedPreferences? = null
        private set

    // the Apps tab's "add an app" nudge: done once the "+" has been used or the nudge put away; Compose state, so the
    // nudge goes the moment either happens
    private val addHint = mutableStateOf(false)
    var addHintDone: Boolean
        get() = addHint.value
        set(value) {
            addHint.value = value
            prefs?.edit()?.putBoolean(KEY_ADD_HINT, value)?.apply()
        }

    fun load(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        addHint.value = prefs?.getBoolean(KEY_ADD_HINT, false) == true
    }

    // every finished install, update or failure passes through here, from wherever it ran
    fun noteActivity(
        action: ActivityAction,
        packageName: String?,
        nowMillis: Long,
    ) {
        val store = prefs ?: return
        val edit = store.edit()
        when (action) {
            ActivityAction.INSTALLED -> {
                val count = store.getInt(KEY_INSTALLS, 0) + 1
                edit.putInt(KEY_INSTALLS, count)
                INSTALL_MILESTONES[count]?.let { edit.putString(KEY_MILESTONE, it) }
            }
            ActivityAction.UPDATED -> {
                val count = store.getInt(KEY_UPDATES, 0) + 1
                edit.putInt(KEY_UPDATES, count)
                UPDATE_MILESTONES[count]?.let { edit.putString(KEY_MILESTONE, it) }
                packageName?.let { edit.putInt(UPDATES_OF + it, store.getInt(UPDATES_OF + it, 0) + 1) }
            }
            ActivityAction.UNINSTALLED -> Unit
            ActivityAction.FAILED -> Unit
        }
        if (action != ActivityAction.UNINSTALLED) KrateWeek.note(store, edit, action, nowMillis)
        edit.apply()
    }

    // a download whose fingerprint matched, before it was allowed anywhere near the installer
    fun noteVerified() {
        val store = prefs ?: return
        store.edit().putInt(KEY_VERIFIED, store.getInt(KEY_VERIFIED, 0) + 1).apply()
    }

    // an app found and tracked from GitHub
    fun noteFind() {
        val store = prefs ?: return
        store.edit().putInt(KEY_FINDS, store.getInt(KEY_FINDS, 0) + 1).apply()
    }

    val hasNoFinds: Boolean
        get() = (prefs?.getInt(KEY_FINDS, 0) ?: 0) == 0

    val verifiedDownloads: Int
        get() = prefs?.getInt(KEY_VERIFIED, 0) ?: 0

    fun updatesOf(packageName: String): Int = prefs?.getInt(UPDATES_OF + packageName, 0) ?: 0

    // the milestone just reached, handed out once
    fun takeMilestone(): String? {
        val milestone = prefs?.getString(KEY_MILESTONE, null)
        if (milestone != null) prefs?.edit()?.remove(KEY_MILESTONE)?.apply()
        return milestone
    }

    // seen each time Home knows how many updates are waiting: a streak starts at the first look with none
    fun notePending(
        count: Int,
        nowMillis: Long,
    ) {
        val store = prefs ?: return
        val since = store.getLong(KEY_CAUGHT_UP_SINCE, 0L)
        when {
            count > 0 && since != 0L -> store.edit().putLong(KEY_CAUGHT_UP_SINCE, 0L).apply()
            count == 0 && since == 0L -> store.edit().putLong(KEY_CAUGHT_UP_SINCE, nowMillis).apply()
        }
    }

    fun caughtUpDays(nowMillis: Long): Int {
        val since = prefs?.getLong(KEY_CAUGHT_UP_SINCE, 0L) ?: 0L
        return if (since == 0L) 0 else ((nowMillis - since) / DAY_MS).toInt()
    }
}
