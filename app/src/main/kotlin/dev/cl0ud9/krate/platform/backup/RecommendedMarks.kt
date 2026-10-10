package dev.cl0ud9.krate.platform.backup

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit

// what was decided about an app's recommended settings
enum class RecommendedChoice {
    // never asked, or the picks have changed since
    UNDECIDED,
    APPLIED,

    // applied an earlier set; the catalog's picks have changed since
    OUTDATED,
    KEPT_OWN,
}

// per app, which set of recommended settings was applied or turned down, by revision, so a changed set is offered
// again while the same one never nags twice
object RecommendedMarks {
    private const val PREFS = "recommended_settings"
    private const val APPLIED = "applied:"
    private const val APPLIED_AT = "applied_at:"
    private const val KEPT = "kept:"

    // bumped on every change, so screens showing the choice redraw at once
    val changes = mutableIntStateOf(0)

    fun choice(
        context: Context,
        packageName: String,
        revision: String,
    ): RecommendedChoice {
        val prefs = prefs(context)
        val applied = prefs.getString(APPLIED + packageName, null)
        return when {
            applied == revision -> RecommendedChoice.APPLIED
            prefs.getString(KEPT + packageName, null) == revision -> RecommendedChoice.KEPT_OWN
            applied != null -> RecommendedChoice.OUTDATED
            else -> RecommendedChoice.UNDECIDED
        }
    }

    fun appliedAt(
        context: Context,
        packageName: String,
    ): Long? = prefs(context).getLong(APPLIED_AT + packageName, 0L).takeIf { it > 0L }

    fun applied(
        context: Context,
        packageName: String,
        revision: String,
    ) {
        prefs(context).edit {
            putString(APPLIED + packageName, revision)
            putLong(APPLIED_AT + packageName, System.currentTimeMillis())
            remove(KEPT + packageName)
        }
        changes.intValue++
    }

    fun keptOwn(
        context: Context,
        packageName: String,
        revision: String,
    ) {
        // keeping their own replaces an earlier "applied", so an undone set doesn't still read as applied
        prefs(context).edit {
            putString(KEPT + packageName, revision)
            remove(APPLIED + packageName)
            remove(APPLIED_AT + packageName)
        }
        changes.intValue++
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
