package dev.cl0ud9.manager.ui.navigation

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.ui.apps.AppsScreen
import dev.cl0ud9.manager.ui.components.HomeChangelogAction
import dev.cl0ud9.manager.ui.details.AppDetailsScreen
import dev.cl0ud9.manager.ui.home.HomeScreen
import dev.cl0ud9.manager.ui.settings.AboutPage
import dev.cl0ud9.manager.ui.settings.AppearanceRoute
import dev.cl0ud9.manager.ui.settings.DownloadsStoragePage
import dev.cl0ud9.manager.ui.settings.FeedbackPage
import dev.cl0ud9.manager.ui.settings.GitHubAccessPage
import dev.cl0ud9.manager.ui.settings.SettingsPageRoute
import dev.cl0ud9.manager.ui.settings.SettingsScreen
import dev.cl0ud9.manager.ui.settings.SettingsShortcutAction
import dev.cl0ud9.manager.ui.updates.UpdatesScreen
import dev.cl0ud9.manager.voice.KrateVoice

private const val APP_DETAILS_ROUTE = "apps/{appId}"
private const val APP_ID_ARG = "appId"

// a plain push (not navigateToTab's popUpTo/saveState dance) so the back button returns to
// whichever tab was showing, and launchSingleTop keeps repeated taps from stacking copies
internal fun NavGraphBuilder.tabDestinations(navController: NavHostController) {
    val openSettings = { navController.navigate(ManagerDestination.SETTINGS.route) { launchSingleTop = true } }
    composable(ManagerDestination.HOME.route) { entry ->
        val context = LocalContext.current
        TabScreen(
            title = stringResource(ManagerDestination.HOME.titleRes),
            navController = navController,
            entry = entry,
            titleIcon = painterResource(R.drawable.ic_krate),
            // picked once per launch; saved state keeps it through tab switches and rotation
            subtitle = rememberSaveable { KrateVoice.greeting(context) },
            actions = {
                HomeChangelogAction()
                SettingsShortcutAction(onClick = openSettings)
            },
        ) {
            HomeScreen(
                onNavigateToApps = { navController.navigateToTab(ManagerDestination.APPS.route) },
                onNavigateToUpdates = { navController.navigateToTab(ManagerDestination.UPDATES.route) },
                onNavigateToApp = { appId -> navController.navigate("apps/$appId") },
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
                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
            )
        }
    }
}

internal fun NavGraphBuilder.appearanceDestination(navController: NavHostController) {
    settingsPageDestination(navController, APPEARANCE_ROUTE, "Appearance") { scrollState, topContentPadding ->
        AppearanceRoute(scrollState = scrollState, topContentPadding = topContentPadding)
    }
}

// the pages behind Settings' category rows - pushed and popped like App Details
internal fun NavGraphBuilder.settingsPageDestinations(navController: NavHostController) {
    settingsPageDestination(navController, SettingsPageRoute.DOWNLOADS, "Downloads & storage") { scroll, top ->
        DownloadsStoragePage(scrollState = scroll, topContentPadding = top)
    }
    settingsPageDestination(navController, SettingsPageRoute.GITHUB, "GitHub access") { scroll, top ->
        GitHubAccessPage(scrollState = scroll, topContentPadding = top)
    }
    settingsPageDestination(navController, SettingsPageRoute.FEEDBACK, "Feedback") { scroll, top ->
        FeedbackPage(scrollState = scroll, topContentPadding = top)
    }
    settingsPageDestination(navController, SettingsPageRoute.ABOUT, "About") { scroll, top ->
        AboutPage(scrollState = scroll, topContentPadding = top)
    }
}

private fun NavGraphBuilder.settingsPageDestination(
    navController: NavHostController,
    route: String,
    title: String,
    content: @Composable (ScrollState, Dp) -> Unit,
) {
    composable(
        route = route,
        enterTransition = { detailsEnterTransition() },
        exitTransition = { detailsExitTransition() },
        popEnterTransition = { detailsPopEnterTransition() },
        popExitTransition = { detailsPopExitTransition() },
    ) { entry ->
        DetailScreen(
            title = title,
            navController = navController,
            entry = entry,
            onBack = { navController.popBackStack() },
        ) { scrollState, topContentPadding ->
            content(scrollState, topContentPadding)
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
