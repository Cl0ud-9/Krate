package dev.cl0ud9.krate.platform.packageinfo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat

// one signal for "an app on the phone was installed, replaced or removed", so everything that shows how an app stands
// (who installed it, its icon, its links, its setup steps) is read again the moment that happens, wherever it
// happens, instead of only when a screen opens or Krate is returned to. Compose reads [version] and so redraws
object PackageChanges {
    val version = mutableIntStateOf(0)

    private var registered = false

    @Synchronized
    fun register(context: Context) {
        if (registered) return
        registered = true
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    version.intValue++
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        // only the system sends these, and it reaches unexported receivers too
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
