package dev.cl0ud9.manager.ui.util

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private const val STAGGER_STEP_MS = 35L
private const val MAX_STAGGERED_ITEMS = 12
private const val FADE_MS = 220

// fade + rise entrance with a short per-index delay, so a first-load list settles in rather than popping
// in all at once - capped so a long list doesn't leave the last items waiting seconds to appear
@Composable
fun StaggeredAppear(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay(index.coerceAtMost(MAX_STAGGERED_ITEMS) * STAGGER_STEP_MS)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            fadeIn(animationSpec = tween(FADE_MS)) +
                slideInVertically(
                    initialOffsetY = { height -> height / 4 },
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                ),
    ) {
        content()
    }
}
