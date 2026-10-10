package dev.cl0ud9.krate.ui.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect

// re-runs onResume every time this screen becomes visible again, e.g. after another tab - installed-app state is
// device-local and can change without the catalog flow ever re-emitting, section 13 + 42.19 of the spec. Android
// finishes an install a moment after its prompt closes, after the screen has already resumed and looked, so while
// the screen is showing, an app going on, coming off or updating (from Krate or anywhere else) refreshes it too
@Composable
fun RefreshOnResume(onResume: () -> Unit) {
    val refresh by rememberUpdatedState(onResume)
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    LifecycleStartEffect(Unit) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = refresh()
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        // only the system sends these, and it reaches unexported receivers too
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
}
