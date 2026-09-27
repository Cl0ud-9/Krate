package dev.cl0ud9.krate.ui.util

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

// which touch, if any, a scrollable box inside the page has claimed; the page's header leaves claimed touches alone
class ScrollClaim {
    var claimed = false
}

val LocalScrollClaim = staticCompositionLocalOf<ScrollClaim?> { null }

// on a page root: every new touch starts unclaimed, before any box inside gets to look at it
fun Modifier.resetsScrollClaim(claim: ScrollClaim): Modifier =
    pointerInput(claim) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            claim.claimed = false
        }
    }

// a box whose content overflows owns any drag that starts in it: the page and its header stay still
fun Modifier.ownsScroll(state: ScrollState): Modifier =
    composed {
        val claim = LocalScrollClaim.current
        val connection = remember { OwnedScrollConnection() }
        pointerInput(claim) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                connection.owned = state.maxValue > 0
                if (connection.owned) claim?.claimed = true
            }
        }.nestedScroll(connection)
    }

private class OwnedScrollConnection : NestedScrollConnection {
    var owned = false

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = if (owned) available else Offset.Zero

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity = if (owned) available else Velocity.Zero
}
