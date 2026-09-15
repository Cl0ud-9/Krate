package dev.cl0ud9.manager.ui.navigation

import android.os.Build
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

// Settings > Appearance's "disable blur" toggle - cheaper to draw on low-end devices, at the cost
// of a heavier dim standing in for the lost blur. Defaults off
val LocalDisableBlur: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

private const val DEPTH_TRANSITION_MS = 350
private val DimBlurEasing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.2f)
private val DEPTH_CORNER_RADIUS = 32.dp
private val DEPTH_BLUR_RADIUS = 24.dp
private const val DEPTH_DIM_ALPHA = 0.4f
private const val DEPTH_DIM_ALPHA_BLUR_DISABLED = 0.75f
private val MIN_VISIBLE_RADIUS = 0.5.dp

// PostExit for a screen a push covers, PreEnter for a screen a predictive-back swipe is revealing -
// the reveal is what makes the swipe-back gesture read as the front screen physically lifting away
private val RECEDING_STATES = setOf(EnterExitState.PostExit, EnterExitState.PreEnter)

private fun isMainRootRoute(route: String?): Boolean = ManagerBottomNavDestinations.any { it.route == route }

// the piece of back-navigation polish a plain slide/fade transition doesn't have: a screen a detail
// view is pushed over doesn't just recede offscreen, it visibly softens first - rounded corners, a
// light dim, and an actual backdrop blur - so the detail screen reads as physically in front of it
// rather than simply beside it
data class DepthEffect(
    val contentModifier: Modifier,
    val dimAlpha: Float,
)

// tab-to-tab switching alone never softens either side, but as soon as a detail screen is part of
// what's visible, every entry involved (the detail screen and whichever tab it covers) takes part -
// corner rounding isn't limited to just the one entry directly behind the front
@Composable
private fun shouldRunDepthEffects(
    navController: NavHostController,
    entry: NavBackStackEntry,
): Boolean {
    val visibleEntries by navController.visibleEntries.collectAsState()
    val isMainRootScreen = isMainRootRoute(entry.destination.route)
    val hasVisibleDetailScreen = visibleEntries.any { !isMainRootRoute(it.destination.route) }
    return !isMainRootScreen || hasVisibleDetailScreen
}

// unlike corner rounding, the dim/blur wash only ever applies to the one entry immediately behind
// the current top - re-reading currentBackStackEntry here is what makes Compose recompute this on
// every navigate/pop rather than comparing against a stale snapshot
@Composable
private fun shouldDim(
    navController: NavHostController,
    entry: NavBackStackEntry,
): Boolean {
    val currentEntry by navController.currentBackStackEntryAsState()
    val previousEntryId = navController.previousBackStackEntry?.id.also { _ -> currentEntry }
    return previousEntryId == entry.id
}

// `entry`/`navController` let this read the live back stack directly instead of taking a
// precomputed boolean - a value handed down through the NavGraphBuilder DSL only ever reflects
// whatever the back stack looked like the one time that DSL block ran to register routes
@Composable
fun AnimatedContentScope.rememberDepthEffect(
    navController: NavHostController,
    entry: NavBackStackEntry,
): DepthEffect {
    // below API 31 there's no RenderEffect/BlurEffect at all, so a blur-less pushed screen would
    // just sit there dimmed with no compensation - same visual gap the user's own toggle exists to
    // avoid, so a device that can't blur is treated exactly like a user who turned blur off
    val blurUnsupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    val disableBlur = LocalDisableBlur.current || blurUnsupported
    val canRound = shouldRunDepthEffects(navController, entry)
    val canDim = canRound && shouldDim(navController, entry)
    val canBlur = canDim && !disableBlur
    val dimAlpha = if (disableBlur) DEPTH_DIM_ALPHA_BLUR_DISABLED else DEPTH_DIM_ALPHA

    val cornerRadius by
        transition.animateDp(
            transitionSpec = { tween(DEPTH_TRANSITION_MS, easing = FastOutSlowInEasing) },
            label = "depthCornerRadius",
        ) { state -> if (canRound && state in RECEDING_STATES) DEPTH_CORNER_RADIUS else 0.dp }

    val animatedDimAlpha by
        transition.animateFloat(
            transitionSpec = { tween(DEPTH_TRANSITION_MS, easing = DimBlurEasing) },
            label = "depthDimAlpha",
        ) { state -> if (canDim && state in RECEDING_STATES) dimAlpha else 0f }

    val blurRadius by
        transition.animateDp(
            transitionSpec = { tween(DEPTH_TRANSITION_MS, easing = DimBlurEasing) },
            label = "depthBlurRadius",
        ) { state -> if (canBlur && state in RECEDING_STATES) DEPTH_BLUR_RADIUS else 0.dp }

    val modifier = depthEffectModifier(cornerRadius, blurRadius)
    return DepthEffect(contentModifier = modifier, dimAlpha = animatedDimAlpha)
}

// plain tab-to-tab switching (the overwhelming majority of navigation) never rounds or blurs
// either side, so it skips this whole extra clip/graphicsLayer node rather than attaching one
// that's merely a no-op at 0dp - that extra offscreen layer on every tab, every frame, was real,
// avoidable overhead behind the choppier feel on tab-to-tab swipes.
// corner rounding starts right where a screen meets the status bar, so on an edge-to-edge layout
// the curve's own height can sit entirely behind the status bar's opaque content for a radius near
// its height - it's still genuinely clipping the whole way down, just not visible until the curve's
// bottom edge clears the status bar (confirmed by forcing a much larger radius and watching it
// appear) - nothing to fix there, but worth remembering next time this looks like it "isn't working"
private fun depthEffectModifier(
    cornerRadius: Dp,
    blurRadius: Dp,
): Modifier {
    if (cornerRadius <= MIN_VISIBLE_RADIUS && blurRadius <= 0.dp) return Modifier
    return Modifier
        .let { if (cornerRadius > MIN_VISIBLE_RADIUS) it.clip(RoundedCornerShape(cornerRadius)) else it }
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            renderEffect =
                if (blurRadius > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BlurEffect(blurRadius.toPx(), blurRadius.toPx(), TileMode.Decal)
                } else {
                    null
                }
        }
}
