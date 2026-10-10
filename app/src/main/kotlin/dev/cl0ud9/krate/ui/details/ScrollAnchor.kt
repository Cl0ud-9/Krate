package dev.cl0ud9.krate.ui.details

import android.os.SystemClock
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

// long enough to cover a section's resize animation after the tap
private const val HOLD_MS = 900L

// keeps a section at the same spot on screen while content above it resizes because of a tap, instead of the page
// jumping. Everything is settled while the page is measured, before the scroll is applied, so it lands in the same
// frame; correcting the scroll after layout always trails the resize by a frame and makes the page bob
@Stable
internal class ScrollAnchor(
    private val scrollState: ScrollState,
) {
    private var holdUntil = 0L
    private var aboveHeight = -1

    // how far the page is drawn above where the scroll puts it: what grew above the section while held, not yet
    // handed to the scroll. Handing it over has to wait for the scroll to have room for it at the page's end
    private var shift = 0

    // content below the section getting shorter (another version's release notes) would pull the page down at its
    // end, so the page keeps the room it had and gives it back only as the user scrolls away from it
    private var floor = 0
    private var lastHeight = 0

    // call right before the tap changes anything above or below the anchored section
    fun hold() {
        holdUntil = SystemClock.uptimeMillis() + HOLD_MS
        floor = maxOf(floor, lastHeight)
    }

    private fun held(): Boolean = !scrollState.isScrollInProgress && SystemClock.uptimeMillis() <= holdUntil

    fun aboveMeasured(height: Int) {
        if (aboveHeight >= 0 && held()) shift += height - aboveHeight
        aboveHeight = height
    }

    // the page's height to lay out at, given what it measured; the page goes at -placeOffset
    fun pageHeight(measured: Int): Int {
        if (shift == 0 && floor == 0) {
            lastHeight = measured
            return measured
        }
        // read so a scroll, or the room it gets, measures the page again
        scrollState.maxValue
        val holding = held()
        if (!holding && shift != 0) {
            val used = scrollState.dispatchRawDelta(shift.toFloat()).roundToInt()
            shift -= used
            if (floor > 0) floor += used
            // at the page's top there's no scroll left to give; the page just moves then
            if (shift < 0) shift = 0
        }
        if (floor > 0) floor = minOf(floor, scrollState.value + scrollState.viewportSize)
        // what didn't fit gets the room now, below the screen, and goes to the scroll next frame
        val room = if (holding) 0 else shift
        lastHeight = maxOf(measured - shift, floor) + room
        return lastHeight
    }

    val placeOffset: Int get() = shift
}

@Composable
internal fun rememberScrollAnchor(scrollState: ScrollState): ScrollAnchor =
    remember(scrollState) { ScrollAnchor(scrollState) }

// on the scrolling page itself, inside its scroll
internal fun Modifier.anchoredPage(anchor: ScrollAnchor): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = anchor.pageHeight(placeable.height)
        layout(placeable.width, height) { placeable.place(0, -anchor.placeOffset) }
    }

// on everything above the anchored section, in one block
internal fun Modifier.aboveAnchor(anchor: ScrollAnchor): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        anchor.aboveMeasured(placeable.height)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
