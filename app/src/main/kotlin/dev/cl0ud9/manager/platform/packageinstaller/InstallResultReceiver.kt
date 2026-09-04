package dev.cl0ud9.manager.platform.packageinstaller

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Parcelable

// key we bake into the pending intent ourselves for uninstall requests, which have no session id of
// their own to correlate on - install requests are keyed off the system's own EXTRA_SESSION_ID instead
const val EXTRA_REQUEST_KEY = "dev.cl0ud9.manager.EXTRA_REQUEST_KEY"

// manifest-registered so a pending session result still reaches us even if the app process was killed
// while android showed its own install confirmation ui, section 42.17 of the spec
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val requestKey = intent.getStringExtra(EXTRA_REQUEST_KEY) ?: "install:$sessionId"
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            launchConfirmation(context, intent)
        }

        InstallResultBus.emit(InstallResultEvent(requestKey = requestKey, status = status, message = message))
    }

    private fun launchConfirmation(
        context: Context,
        intent: Intent,
    ) {
        val confirmationIntent = intent.parcelableExtraCompat<Intent>(Intent.EXTRA_INTENT) ?: return
        confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(confirmationIntent)
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : Parcelable> Intent.parcelableExtraCompat(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            getParcelableExtra(key)
        }
}

// api 31+ requires pending intents to declare mutability explicitly; below that, flag doesn't exist
fun installResultPendingIntentFlags(): Int {
    var flags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        flags = flags or PendingIntent.FLAG_MUTABLE
    }
    return flags
}
