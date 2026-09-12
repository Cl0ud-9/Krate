package dev.cl0ud9.manager.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

// Material's own published Emphasized-easing tokens (see the motion spec: "emphasized decelerate"
// for elements entering the screen, "emphasized accelerate" for elements leaving) - straight from
// the public M3 motion spec
private val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val PUSH_POP_DURATION_MS = 400

// parallax push/pop for the App Details route: the entering screen travels the full distance while
// the outgoing one only recedes a quarter of the way (and vice versa on the way back) - reads as the
// new screen genuinely arriving on top of the old one, rather than the two screens swapping places
// at equal speed
internal fun detailsEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(PUSH_POP_DURATION_MS, easing = EmphasizedDecelerateEasing),
        initialOffsetX = { fullWidth -> fullWidth },
    ) + fadeIn(animationSpec = tween(PUSH_POP_DURATION_MS, easing = EmphasizedDecelerateEasing))

internal fun detailsExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(PUSH_POP_DURATION_MS, easing = EmphasizedAccelerateEasing),
        targetOffsetX = { fullWidth -> -(fullWidth / 4) },
    ) + fadeOut(animationSpec = tween(PUSH_POP_DURATION_MS / 2, easing = EmphasizedAccelerateEasing))

internal fun detailsPopEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(PUSH_POP_DURATION_MS, easing = EmphasizedDecelerateEasing),
        initialOffsetX = { fullWidth -> -(fullWidth / 4) },
    ) + fadeIn(animationSpec = tween(PUSH_POP_DURATION_MS / 2, easing = EmphasizedDecelerateEasing))

internal fun detailsPopExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(PUSH_POP_DURATION_MS, easing = EmphasizedAccelerateEasing),
        targetOffsetX = { fullWidth -> fullWidth },
    ) + fadeOut(animationSpec = tween(PUSH_POP_DURATION_MS / 2, easing = EmphasizedAccelerateEasing))
