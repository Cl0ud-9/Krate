package dev.cl0ud9.manager.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private const val DEPTH_TRANSITION_MS = 350
private val DepthEasing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.2f)
private val DEPTH_CORNER_RADIUS = 28.dp
private val DEPTH_BLUR_RADIUS = 20.dp
private const val DEPTH_DIM_ALPHA = 0.35f
private val MIN_VISIBLE_RADIUS = 0.5.dp
private val RECEDING_STATES = setOf(EnterExitState.PostExit, EnterExitState.PreEnter)

// the piece of back-navigation polish a plain slide/fade transition doesn't have: a screen a detail
// view is pushed over doesn't just recede offscreen, it visibly softens first - rounded corners, a
// light dim, and an actual backdrop blur - so the detail screen reads as physically in front of it
// rather than simply beside it. `active` is computed by the caller (see ManagerNavHost's use of
// NavController.visibleEntries) rather than assumed here, since only the caller knows which entry
// is genuinely covered versus genuinely on top.
data class DepthEffect(
    val contentModifier: Modifier,
    val dimAlpha: Float,
)

@Composable
fun AnimatedContentScope.rememberDepthEffect(active: Boolean): DepthEffect {
    val cornerRadius by
        transition.animateDp(
            transitionSpec = { tween(DEPTH_TRANSITION_MS, easing = DepthEasing) },
            label = "depthCornerRadius",
        ) { state -> if (active && state in RECEDING_STATES) DEPTH_CORNER_RADIUS else 0.dp }

    val blurRadius by
        transition.animateDp(
            transitionSpec = { tween(DEPTH_TRANSITION_MS, easing = DepthEasing) },
            label = "depthBlurRadius",
        ) { state -> if (active && state in RECEDING_STATES) DEPTH_BLUR_RADIUS else 0.dp }

    val dimAlpha by
        transition.animateFloat(
            transitionSpec = { tween(DEPTH_TRANSITION_MS, easing = DepthEasing) },
            label = "depthDimAlpha",
        ) { state -> if (active && state in RECEDING_STATES) DEPTH_DIM_ALPHA else 0f }

    val modifier =
        Modifier
            .graphicsLayer {
                compositingStrategy = if (active) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                if (cornerRadius > MIN_VISIBLE_RADIUS) {
                    shape = RoundedCornerShape(cornerRadius)
                    clip = true
                } else {
                    clip = false
                }
            }.blur(blurRadius)

    return DepthEffect(contentModifier = modifier, dimAlpha = dimAlpha)
}
