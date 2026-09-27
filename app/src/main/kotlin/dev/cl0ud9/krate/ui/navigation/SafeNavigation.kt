package dev.cl0ud9.krate.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

// a tap landing while a screen is still arriving is a double tap, not a new intent
private fun NavHostController.isSettled(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true

// a push from a tap, ignored while a transition is still running; the same exact route is never stacked
// twice, but a different app's page (same destination, other arguments) opens as a new screen
internal fun NavHostController.navigateFromTap(route: String) {
    if (!isSettled()) return
    if (currentBackStackEntry?.let(::currentRouteOf) == route) return
    navigate(route)
}

private val ROUTE_ARGUMENT = Regex("""\{([^}]+)\}""")

// the concrete route of an entry, with its arguments filled in
private fun currentRouteOf(entry: NavBackStackEntry): String? =
    entry.destination.route?.let { template ->
        ROUTE_ARGUMENT.replace(template) { match -> entry.arguments?.getString(match.groupValues[1]) ?: match.value }
    }

// a back from a tap: a second tap during the pop animation would otherwise pop the screen underneath too
internal fun NavHostController.popBackFromTap() {
    if (!isSettled()) return
    popBackStack()
}
