package dev.cl0ud9.manager.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
private const val TAB_ENTER_INITIAL_SCALE = 0.94f

@Composable
fun ManagerNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = ManagerDestination.entries.any { it.route == currentRoute }

    Scaffold(
        topBar = { ManagerTopBar(navController, currentRoute) },
        bottomBar = { if (showBottomBar) ManagerBottomBar(navController, currentRoute) },
    ) { innerPadding ->
        ManagerNavGraph(navController, modifier = Modifier.padding(innerPadding))
    }
}

// every screen gets a real M3 top app bar instead of ad-hoc per-screen headers/titles -
// tab destinations show their title, the app-details route gets a back affordance
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagerTopBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    val destination = ManagerDestination.entries.firstOrNull { it.route == currentRoute }
    when {
        destination != null -> {
            TopAppBar(
                title = { Text(stringResource(destination.titleRes), style = MaterialTheme.typography.headlineSmall) },
            )
        }

        currentRoute == APP_DETAILS_ROUTE -> {
            TopAppBar(
                title = { Text("App Details", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    }
}

@Composable
private fun ManagerBottomBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    NavigationBar {
        ManagerDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            ManagerNavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = if (selected) destination.selectedIcon else destination.unselectedIcon,
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
        // material "fade through": the incoming tab fades and grows in while the outgoing one just fades
        enterTransition = {
            fadeIn(animationSpec = tween(FADE_DURATION_MS)) +
                scaleIn(initialScale = TAB_ENTER_INITIAL_SCALE, animationSpec = tween(FADE_DURATION_MS))
        },
        exitTransition = { fadeOut(animationSpec = tween(FADE_DURATION_MS)) },
    ) {
        composable(ManagerDestination.HOME.route) { OpaqueScreen { HomeScreen() } }
        composable(ManagerDestination.APPS.route) {
            OpaqueScreen { AppsScreen(onAppClick = { appId -> navController.navigate("apps/$appId") }) }
        }
        composable(ManagerDestination.UPDATES.route) {
            OpaqueScreen { UpdatesScreen(onAppClick = { appId -> navController.navigate("apps/$appId") }) }
        }
        composable(ManagerDestination.SETTINGS.route) { OpaqueScreen { SettingsScreen() } }

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
            OpaqueScreen {
                AppDetailsScreen(
                    appId = appId,
                    onNavigateToApp = { dependencyId -> navController.navigate("apps/$dependencyId") },
                )
            }
        }
    }
}

// every destination's content sits on its own opaque backdrop - without this, a fade-based transition
// (used by every route above) blends the outgoing screen's text with the incoming screen's, since
// both would otherwise draw straight onto the single shared background behind the whole NavHost
@Composable
private fun OpaqueScreen(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        content()
    }
}
