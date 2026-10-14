package dev.cl0ud9.krate.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

// runs [clear] every time this screen is in front: a notification that points here has nothing left to say once the
// person is looking at the thing it pointed to, however they got here, and leaving it in the shade is how a tap on it
// later seems to do nothing
@Composable
fun ClearNoticesOnResume(clear: (Context) -> Unit) {
    val context = LocalContext.current
    val current by rememberUpdatedState(clear)
    LifecycleResumeEffect(Unit) {
        current(context)
        onPauseOrDispose { }
    }
}
