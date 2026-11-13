package dev.cl0ud9.krate.platform.backup

import android.content.Context
import androidx.core.content.edit

// whether Krate leaves its accessibility helper switched on once it has done its job. Off by default: some banking and
// payment apps refuse to open while any accessibility service is on, so the helper switches itself off when it's idle
object HelperKeepOn {
    private const val PREFS = "krate_helper"
    private const val KEY = "keep_on"

    fun get(context: Context): Boolean = prefs(context).getBoolean(KEY, false)

    fun set(
        context: Context,
        keepOn: Boolean,
    ) = prefs(context).edit { putBoolean(KEY, keepOn) }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
