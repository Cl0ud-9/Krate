package dev.cl0ud9.krate.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val END_SLACK_MS = 250L

// the time as of now, read again the moment [untilMillis] passes (and when it changes), so whatever is worked out from
// "is this still going on" turns over by itself with the screen open instead of waiting for the next touch
@Composable
fun rememberClockUntil(untilMillis: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(untilMillis) {
        now = System.currentTimeMillis()
        val wait = untilMillis - now
        if (wait > 0) {
            delay(wait + END_SLACK_MS)
            now = System.currentTimeMillis()
        }
    }
    return now
}
