package dev.cl0ud9.manager.ui.util

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEFAULT_DEBOUNCE_MS = 500L

// pairs with rememberDebouncedOnClick below: a button wrapped in this goes properly disabled for
// the cooldown instead of merely having its callback swallowed, so a fast repeat tap can't also
// stack up a second ripple/indication animation on top of the first while it's still playing
data class DebouncedButtonState(
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun rememberDebouncedButtonState(
    intervalMs: Long = DEFAULT_DEBOUNCE_MS,
    onClick: () -> Unit,
): DebouncedButtonState {
    var enabled by remember { mutableStateOf(true) }
    val latestOnClick = rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()
    val click =
        remember(intervalMs) {
            {
                if (enabled) {
                    enabled = false
                    latestOnClick.value()
                    scope.launch {
                        delay(intervalMs)
                        enabled = true
                    }
                }
            }
        }
    return DebouncedButtonState(enabled = enabled, onClick = click)
}

// wraps a click handler so a fast double/triple-tap - a real finger bouncing, or a tap landing
// while the previous one's recomposition (an AnimatedContent crossfade, a state flip) hasn't
// settled yet - only ever fires once. A ViewModel-level guard already makes the underlying
// operation itself safe to repeat; this is the belt to that suspenders, stopping the extra taps
// at the UI edge instead of relying on every call site remembering its own guard correctly.
// SystemClock.elapsedRealtime(), not a Compose-scoped timer, so it survives recomposition
@Composable
fun rememberDebouncedOnClick(
    intervalMs: Long = DEFAULT_DEBOUNCE_MS,
    onClick: () -> Unit,
): () -> Unit {
    val latestOnClick = rememberUpdatedState(onClick)
    val lastClickAt = remember { longArrayOf(0L) }
    return remember(intervalMs) {
        {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickAt[0] >= intervalMs) {
                lastClickAt[0] = now
                latestOnClick.value()
            }
        }
    }
}
