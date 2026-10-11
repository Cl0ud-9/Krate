package dev.cl0ud9.krate.platform.setup

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit

// how far someone has got through an app's step-by-step setup, per app, so leaving and coming back picks up where they
// were, and a finished one reads as finished. Kept only on this phone
object WalkthroughMarks {
    private const val PREFS = "walkthrough"
    private const val STEP = "step:"
    private const val DONE = "done:"

    // bumped on every change, so a row showing the progress redraws at once
    val changes = mutableIntStateOf(0)

    fun step(
        context: Context,
        packageName: String,
        count: Int,
    ): Int = prefs(context).getInt(STEP + packageName, 0).coerceIn(0, (count - 1).coerceAtLeast(0))

    fun finished(
        context: Context,
        packageName: String,
    ): Boolean = prefs(context).getBoolean(DONE + packageName, false)

    fun moveTo(
        context: Context,
        packageName: String,
        step: Int,
    ) {
        prefs(context).edit { putInt(STEP + packageName, step.coerceAtLeast(0)) }
        changes.intValue++
    }

    fun finish(
        context: Context,
        packageName: String,
    ) {
        prefs(context).edit { putBoolean(DONE + packageName, true) }
        changes.intValue++
    }

    // start again from the first step
    fun reset(
        context: Context,
        packageName: String,
    ) {
        prefs(context).edit {
            remove(STEP + packageName)
            remove(DONE + packageName)
        }
        changes.intValue++
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
