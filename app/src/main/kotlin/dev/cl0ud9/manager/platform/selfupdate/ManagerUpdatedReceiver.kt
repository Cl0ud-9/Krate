package dev.cl0ud9.manager.platform.selfupdate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.cl0ud9.manager.platform.workers.UpdateNotifier

private const val PREFS = "self_update"
private const val KEY_PENDING = "pending"

// Android closes an app while installing an update to it and doesn't let it reopen itself from the
// background, so after an in-app self-update the user is left on the home screen. This posts a
// "tap to open" notification once the new version is in place - only when the update was started
// from inside the app, not for any other reinstall
class ManagerUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return
        prefs.edit().remove(KEY_PENDING).apply()
        val version =
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        UpdateNotifier.notifyManagerUpdated(context, version)
    }

    companion object {
        // set right before the downloaded update is handed to Android's installer
        fun markSelfUpdatePending(context: Context) {
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PENDING, true)
                .apply()
        }
    }
}
