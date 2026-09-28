package dev.cl0ud9.krate.ui.util

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

private const val STAGGER_STEP_MS = 35L
private const val MAX_STAGGERED_ITEMS = 12
private const val RISE_PX = 40f

// only items there when the list first shows get the entrance; one scrolled into view later just appears
private const val ENTRANCE_WINDOW_MS = 700L

// when a list first showed, so its items can tell an opening entrance from being scrolled into view
@Composable
fun rememberListShownAt(): Long = rememberSaveable { SystemClock.uptimeMillis() }

// fade + rise entrance with a short per-index delay, so a first-load list settles in rather than popping in all at
// once. Each item remembers it has appeared even after scrolling away, and the motion is drawn only (the item keeps
// its full height throughout), so flicking back up never replays it or nudges the list
@Composable
fun StaggeredAppear(
    index: Int,
    listShownAt: Long,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var shown by rememberSaveable {
        mutableStateOf(SystemClock.uptimeMillis() - listShownAt > ENTRANCE_WINDOW_MS)
    }
    val progress = remember { Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay(index.coerceAtMost(MAX_STAGGERED_ITEMS) * STAGGER_STEP_MS)
            progress.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            )
            shown = true
        }
    }
    Box(
        modifier =
            modifier.graphicsLayer {
                alpha = progress.value.coerceIn(0f, 1f)
                translationY = (1f - progress.value) * RISE_PX
            },
    ) { content() }
}
