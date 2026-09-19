package dev.cl0ud9.manager.ui.navigation

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.cl0ud9.manager.ui.apps.AppsScreen
import dev.cl0ud9.manager.ui.components.HomeChangelogAction
import dev.cl0ud9.manager.ui.details.AppDetailsScreen
import dev.cl0ud9.manager.ui.home.HomeScreen
import dev.cl0ud9.manager.ui.settings.AppearanceRoute
import dev.cl0ud9.manager.ui.settings.SettingsScreen
import dev.cl0ud9.manager.ui.settings.SettingsShortcutAction
import dev.cl0ud9.manager.ui.updates.UpdatesScreen

private const val APP_DETAILS_ROUTE = "apps/{appId}"
private const val APP_ID_ARG = "appId"

// a plain push (not navigateToTab's popUpTo/saveState dance) so the back button returns to
// whichever tab was showing, and launchSingleTop keeps repeated taps from stacking copies
internal fun NavGraphBuilder.tabDestinations(navController: NavHostController) {
    val openSettings = { navController.navigate(ManagerDestination.SETTINGS.route) { launchSingleTop = true } }
    composable(ManagerDestination.HOME.route) { entry ->
        TabScreen(
            title = stringResource(ManagerDestination.HOME.titleRes),
            navController = navController,
            entry = entry,
            actions = {
                HomeChangelogAction()
                SettingsShortcutAction(onClick = openSettings)
            },
        ) {
            HomeScreen(
                onNavigateToApps = { navController.navigateToTab(ManagerDestination.APPS.route) },
                onNavigateToUpdates = { navController.navigateToTab(ManagerDestination.UPDATES.route) },
            )
        }
    }
    composable(ManagerDestination.APPS.route) { entry ->
        TabScreen(
            title = stringResource(ManagerDestination.APPS.titleRes),
            navController = navController,
            entry = entry,
            actions = { SettingsShortcutAction(onClick = openSettings) },
        ) {
            AppsScreen(onAppClick = { appId -> navController.navigate("apps/$appId") })
        }
    }
    composable(ManagerDestination.UPDATES.route) { entry ->
        TabScreen(
            title = stringResource(ManagerDestination.UPDATES.titleRes),
            navController = navController,
            entry = entry,
            actions = { SettingsShortcutAction(onClick = openSettings) },
        ) {
            UpdatesScreen(onAppClick = { appId -> navController.navigate("apps/$appId") })
        }
    }
}

// Settings is reached via the shortcut above, not a bottom-nav tab, so it's pushed and popped like
// the App Details route - same transitions, same back affordance - rather than a directional tab slide
internal fun NavGraphBuilder.settingsDestination(navController: NavHostController) {
    composable(
        route = ManagerDestination.SETTINGS.route,
        enterTransition = { detailsEnterTransition() },
        exitTransition = { detailsExitTransition() },
        popEnterTransition = { detailsPopEnterTransition() },
        popExitTransition = { detailsPopExitTransition() },
    ) { entry ->
        DetailScreen(
            title = stringResource(ManagerDestination.SETTINGS.titleRes),
            navController = navController,
            entry = entry,
            onBack = { navController.popBackStack() },
        ) { scrollState, topContentPadding ->
            SettingsScreen(
                scrollState = scrollState,
                topContentPadding = topContentPadding,
                onNavigateToAppearance = { navController.navigate(APPEARANCE_ROUTE) },
            )
        }
    }
}

internal fun NavGraphBuilder.appearanceDestination(navController: NavHostController) {
    composable(
        route = APPEARANCE_ROUTE,
        enterTransition = { detailsEnterTransition() },
        exitTransition = { detailsExitTransition() },
        popEnterTransition = { detailsPopEnterTransition() },
        popExitTransition = { detailsPopExitTransition() },
    ) { entry ->
        DetailScreen(
            title = "Appearance",
            navController = navController,
            entry = entry,
            onBack = { navController.popBackStack() },
        ) { scrollState, topContentPadding ->
            AppearanceRoute(scrollState = scrollState, topContentPadding = topContentPadding)
        }
    }
}

internal fun NavGraphBuilder.appDetailsDestination(navController: NavHostController) {
    composable(
        route = APP_DETAILS_ROUTE,
        arguments = listOf(navArgument(APP_ID_ARG) { type = NavType.StringType }),
        enterTransition = { detailsEnterTransition() },
        exitTransition = { detailsExitTransition() },
        popEnterTransition = { detailsPopEnterTransition() },
        popExitTransition = { detailsPopExitTransition() },
    ) { entry ->
        val appId = entry.arguments?.getString(APP_ID_ARG).orEmpty()
        DetailScreen(
            title = "App Details",
            navController = navController,
            entry = entry,
            onBack = { navController.popBackStack() },
        ) { scrollState, topContentPadding ->
            AppDetailsScreen(
                appId = appId,
                onNavigateToApp = { dependencyId -> navController.navigate("apps/$dependencyId") },
                scrollState = scrollState,
                topContentPadding = topContentPadding,
            )
        }
    }
}
