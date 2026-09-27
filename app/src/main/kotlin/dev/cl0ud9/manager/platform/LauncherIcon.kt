package dev.cl0ud9.manager.platform

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

// the launcher icon: off in the manifest, so hiding it leaves no stand-in icon; Krate switches it on unless hidden
object LauncherIcon {
    private const val ICON_ENTRY = "dev.cl0ud9.manager.MainActivity"

    private fun entry(context: Context) = ComponentName(context.packageName, ICON_ENTRY)

    fun isHidden(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(entry(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    // no choice recorded yet (a fresh install, or updated from before this existed) means visible
    fun showUnlessHidden(context: Context) {
        val state = context.packageManager.getComponentEnabledSetting(entry(context))
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) setHidden(context, hidden = false)
    }

    fun setHidden(
        context: Context,
        hidden: Boolean,
    ) {
        val state =
            if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
        context.packageManager.setComponentEnabledSetting(entry(context), state, PackageManager.DONT_KILL_APP)
    }
}

// runs right after Krate is updated, so the icon comes back without Krate having to be opened first
class LauncherIconReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) LauncherIcon.showUnlessHidden(context)
    }
}
