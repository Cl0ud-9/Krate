package dev.cl0ud9.krate.ui.navigation

import android.os.SystemClock
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

// the public M3 emphasized-easing token, same curve regardless of who's implementing the spec.
// internal (not private): KrateNavHost's nav bar hide/show tracks this exact spec too, so the
// bar finishes its slide at the same instant as the screen it's making way for instead of two
// independently-timed animations drifting apart mid-transition
internal val M3EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// slow start, fast finish - used only for the pop-exit slide, so the outgoing screen barely moves
// at first and then leaves all at once, instead of a constant-speed slide the whole way
private val CubicInEasing = Easing { fraction -> fraction * fraction * fraction }
internal const val DETAIL_TRANSITION_MS = 350

// push/pop for the App Details route. Both directions travel only a third of the screen width and
// fade in on the way in; on the way out the departing screen also shrinks (scaleOut) instead of
// just sliding, which combined with the corner/blur/dim depth effect underneath reads as the
// screen genuinely receding in space rather than two flat panels swapping places at equal speed.
// The pop-exit shrinks further (0.85 vs 0.92) and slides the full width, since it's leaving for good
internal fun detailsEnterTransition(): EnterTransition =
    if (ArrivalFromOutside.active) {
        EnterTransition.None
    } else {
        slideInHorizontally(
            animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing),
            initialOffsetX = { fullWidth -> fullWidth / 3 },
        ) + fadeIn(animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing))
    }

internal fun detailsExitTransition(): ExitTransition =
    if (ArrivalFromOutside.active) {
        ExitTransition.None
    } else {
        slideOutHorizontally(
            animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing),
            targetOffsetX = { fullWidth -> -(fullWidth / 3) },
        ) + scaleOut(targetScale = 0.92f, animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing))
    }

internal fun detailsPopEnterTransition(): EnterTransition =
    if (ArrivalFromOutside.active) {
        EnterTransition.None
    } else {
        slideInHorizontally(
            animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing),
            initialOffsetX = { fullWidth -> -(fullWidth / 3) },
        ) + fadeIn(animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing))
    }

internal fun detailsPopExitTransition(): ExitTransition =
    if (ArrivalFromOutside.active) {
        ExitTransition.None
    } else {
        slideOutHorizontally(
            animationSpec = tween(DETAIL_TRANSITION_MS, easing = CubicInEasing),
            targetOffsetX = { fullWidth -> fullWidth },
        ) + scaleOut(targetScale = 0.85f, animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing))
    }

// a change is a step back when it heads for the page under the current one, or leaves a page that's no longer right
// under where it lands, whatever Navigation calls it: a quick back swipe on a page still arriving ended as a plain
// change, not a pop, and slid the page off to the left like a step forward
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.isStepBack(navController: NavHostController): Boolean =
    isStepBack(
        current = navController.currentBackStackEntry?.id,
        previous = navController.previousBackStackEntry?.id,
        from = initialState.id,
        to = targetState.id,
    )

internal fun isStepBack(
    current: String?,
    previous: String?,
    from: String,
    to: String,
): Boolean = (from == current && to == previous) || (to == current && from != previous)

// a pushed page's own enter and exit, the back ones whenever the change is a step back
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pushedPageEnter(
    navController: NavHostController,
): EnterTransition = if (isStepBack(navController)) detailsPopEnterTransition() else detailsEnterTransition()

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pushedPageExit(
    navController: NavHostController,
): ExitTransition = if (isStepBack(navController)) detailsPopExitTransition() else detailsExitTransition()

// a notification bringing Krate back from the background lands straight on its screen: the system's launch
// animation is already the transition, so an in-app push on top of it would play a second one
internal object ArrivalFromOutside {
    // only a safety net: it normally ends as soon as the target screen is in place, however long a cold start takes
    private const val SAFETY_MS = 5_000L

    @Volatile
    private var until = 0L

    fun arm() {
        until = SystemClock.uptimeMillis() + SAFETY_MS
    }

    fun disarm() {
        until = 0L
    }

    val active: Boolean
        get() = SystemClock.uptimeMillis() < until
}
