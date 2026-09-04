package dev.cl0ud9.manager.ui.util

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect

// re-runs onResume every time this screen becomes visible again, e.g. after returning from an
// install confirmation or another tab - installed-app state is device-local and can change
// without the catalog flow ever re-emitting, section 13 + 42.19 of the spec
@Composable
fun RefreshOnResume(onResume: () -> Unit) {
    LifecycleResumeEffect(Unit) {
        onResume()
        onPauseOrDispose { }
    }
}
