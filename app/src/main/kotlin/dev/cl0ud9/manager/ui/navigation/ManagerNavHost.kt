package dev.cl0ud9.manager.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.cl0ud9.manager.ui.apps.AppsScreen
import dev.cl0ud9.manager.ui.components.ManagerNavigationBarItem
import dev.cl0ud9.manager.ui.details.AppDetailsScreen
import dev.cl0ud9.manager.ui.home.HomeScreen
import dev.cl0ud9.manager.ui.settings.SettingsScreen
import dev.cl0ud9.manager.ui.updates.UpdatesScreen

private const val APP_DETAILS_ROUTE = "apps/{appId}"
private const val APP_ID_ARG = "appId"
private const val FADE_DURATION_MS = 180

@Composable
fun ManagerNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = ManagerDestination.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = { if (showBottomBar) ManagerBottomBar(navController, currentRoute) },
    ) { innerPadding ->
        ManagerNavGraph(navController, modifier = Modifier.padding(innerPadding))
    }
}

@Composable
private fun ManagerBottomBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    NavigationBar {
        ManagerDestination.entries.forEach { destination ->
            ManagerNavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = destination.icon,
                label = stringResource(destination.labelRes),
            )
        }
    }
}

@Composable
private fun ManagerNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = ManagerDestination.HOME.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(FADE_DURATION_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(FADE_DURATION_MS)) },
    ) {
        composable(ManagerDestination.HOME.route) { HomeScreen() }
        composable(ManagerDestination.APPS.route) {
            AppsScreen(onAppClick = { appId -> navController.navigate("apps/$appId") })
        }
        composable(ManagerDestination.UPDATES.route) { UpdatesScreen() }
        composable(ManagerDestination.SETTINGS.route) { SettingsScreen() }

        composable(
            route = APP_DETAILS_ROUTE,
            arguments = listOf(navArgument(APP_ID_ARG) { type = NavType.StringType }),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                ) + fadeIn(animationSpec = tween(FADE_DURATION_MS))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                ) + fadeOut(animationSpec = tween(FADE_DURATION_MS))
            },
            popEnterTransition = { fadeIn(animationSpec = tween(FADE_DURATION_MS)) },
            exitTransition = { fadeOut(animationSpec = tween(FADE_DURATION_MS)) },
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getString(APP_ID_ARG).orEmpty()
            AppDetailsScreen(appId = appId, onBack = { navController.popBackStack() })
        }
    }
}
