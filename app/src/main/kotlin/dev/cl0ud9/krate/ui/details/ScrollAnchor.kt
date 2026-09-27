package dev.cl0ud9.krate.ui.details

import android.os.SystemClock
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent

// long enough to cover a section's resize animation after the tap
private const val HOLD_MS = 900L

// keeps a section at the same spot on screen while content above it resizes because of a tap, instead of the
// page jumping. It aims for the on-screen spot itself, so the scroll clamping at the page's end can't throw it off
@Stable
internal class ScrollAnchor(
    private val scrollState: ScrollState,
) {
    private var holdUntil = 0L
    private var lastY: Float? = null
    private var targetOnScreen: Float? = null

    // call right before the tap changes anything above the anchored section
    fun hold() {
        holdUntil = SystemClock.uptimeMillis() + HOLD_MS
        targetOnScreen = lastY?.let { it - scrollState.value }
    }

    fun onPositioned(y: Float) {
        val resized = y != lastY
        lastY = y
        val target = targetOnScreen
        when {
            target == null -> Unit
            // the user's own scroll takes over at once; the anchor only ever answers a resize above it
            scrollState.isScrollInProgress || SystemClock.uptimeMillis() > holdUntil -> targetOnScreen = null
            resized -> {
                val drift = y - scrollState.value - target
                if (drift != 0f) scrollState.dispatchRawDelta(drift)
            }
        }
    }
}

@Composable
internal fun rememberScrollAnchor(scrollState: ScrollState): ScrollAnchor =
    remember(scrollState) { ScrollAnchor(scrollState) }

// the section's place in the scrolling column, which doesn't move with the scroll itself. Read while the layout is
// placed rather than after it, so the correction lands in the same frame instead of showing a one-frame bump
internal fun Modifier.anchoredBy(anchor: ScrollAnchor): Modifier =
    onPlaced { anchor.onPositioned(it.positionInParent().y) }
