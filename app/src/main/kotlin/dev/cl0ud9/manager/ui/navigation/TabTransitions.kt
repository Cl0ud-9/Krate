package dev.cl0ud9.manager.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset

// tab switches slide directionally by the tapped tab's position instead of just fading - matches how
// an index-ordered set of top-level destinations reads as one continuous strip being panned across
private const val TAB_TRANSITION_MS = 380
private const val FADE_DURATION_MS = 180
private const val TAB_ENTER_INITIAL_SCALE = 0.94f
private val TabTransitionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val TabOffsetSpec = tween<IntOffset>(TAB_TRANSITION_MS, easing = TabTransitionEasing)
private val TabFadeSpec = tween<Float>(TAB_TRANSITION_MS / 2, easing = TabTransitionEasing)

private fun tabIndex(route: String?): Int? =
    ManagerBottomNavDestinations.indexOfFirst { it.route == route }.takeIf { it >= 0 }

// positive moving to a later tab, negative moving to an earlier one, null when either side isn't a
// top-level tab at all (the App Details route, which has its own push/pop transitions)
private fun tabDirection(
    fromRoute: String?,
    toRoute: String?,
): Int? {
    val from = tabIndex(fromRoute)
    val to = tabIndex(toRoute)
    return when {
        from == null || to == null || from == to -> null
        to > from -> 1
        else -> -1
    }
}

internal fun tabEnterTransition(
    fromRoute: String?,
    toRoute: String?,
): EnterTransition {
    val direction = tabDirection(fromRoute, toRoute) ?: return fallbackEnterTransition()
    return slideInHorizontally(TabOffsetSpec) { fullWidth -> direction * (fullWidth / 2) } + fadeIn(TabFadeSpec)
}

internal fun tabExitTransition(
    fromRoute: String?,
    toRoute: String?,
): ExitTransition {
    val direction = tabDirection(fromRoute, toRoute) ?: return fadeOut(animationSpec = tween(FADE_DURATION_MS))
    return slideOutHorizontally(TabOffsetSpec) { fullWidth -> -direction * (fullWidth / 2) } + fadeOut(TabFadeSpec)
}

// used only when there's no "from" tab to compare against (e.g. the very first frame)
private fun fallbackEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(FADE_DURATION_MS)) +
        scaleIn(initialScale = TAB_ENTER_INITIAL_SCALE, animationSpec = tween(FADE_DURATION_MS))
