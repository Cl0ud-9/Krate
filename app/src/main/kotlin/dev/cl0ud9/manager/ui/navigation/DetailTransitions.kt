package dev.cl0ud9.manager.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

// the public M3 emphasized-easing token, same curve regardless of who's implementing the spec
private val M3EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// slow start, fast finish - used only for the pop-exit slide, so the outgoing screen barely moves
// at first and then leaves all at once, instead of a constant-speed slide the whole way
private val CubicInEasing = Easing { fraction -> fraction * fraction * fraction }
private const val DETAIL_TRANSITION_MS = 350

// push/pop for the App Details route. Both directions travel only a third of the screen width and
// fade in on the way in; on the way out the departing screen also shrinks (scaleOut) instead of
// just sliding, which combined with the corner/blur/dim depth effect underneath reads as the
// screen genuinely receding in space rather than two flat panels swapping places at equal speed.
// The pop-exit shrinks further (0.85 vs 0.92) and slides the full width, since it's leaving for good
internal fun detailsEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing),
        initialOffsetX = { fullWidth -> fullWidth / 3 },
    ) + fadeIn(animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing))

internal fun detailsExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing),
        targetOffsetX = { fullWidth -> -(fullWidth / 3) },
    ) + scaleOut(targetScale = 0.92f, animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing))

internal fun detailsPopEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing),
        initialOffsetX = { fullWidth -> -(fullWidth / 3) },
    ) + fadeIn(animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing))

internal fun detailsPopExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(DETAIL_TRANSITION_MS, easing = CubicInEasing),
        targetOffsetX = { fullWidth -> fullWidth },
    ) + scaleOut(targetScale = 0.85f, animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing))
