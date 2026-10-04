package dev.cl0ud9.krate.ui.navigation

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.apps.AppsScreen
import dev.cl0ud9.krate.ui.apps.rememberAppsSubtitle
import dev.cl0ud9.krate.ui.components.HomeChangelogAction
import dev.cl0ud9.krate.ui.details.AppDetailsScreen
import dev.cl0ud9.krate.ui.home.HomeScreen
import dev.cl0ud9.krate.ui.settings.AboutPage
import dev.cl0ud9.krate.ui.settings.AppearanceRoute
import dev.cl0ud9.krate.ui.settings.BackupsPage
import dev.cl0ud9.krate.ui.settings.CornerRadiusEditor
import dev.cl0ud9.krate.ui.settings.DownloadsStoragePage
import dev.cl0ud9.krate.ui.settings.FeedbackPage
import dev.cl0ud9.krate.ui.settings.GitHubAccessPage
import dev.cl0ud9.krate.ui.settings.SettingsPageRoute
import dev.cl0ud9.krate.ui.settings.SettingsScreen
import dev.cl0ud9.krate.ui.settings.SettingsShortcutAction
import dev.cl0ud9.krate.ui.updates.UpdatesScreen
import dev.cl0ud9.krate.ui.updates.rememberUpdatesSubtitle
import dev.cl0ud9.krate.voice.rememberKrateGreeting

private const val CORNER_RADIUS_ROUTE = "settings/appearance/corner-radius"
private const val APP_DETAILS_ROUTE = "apps/{appId}"
private const val APP_ID_ARG = "appId"

// a plain push (not navigateToTab's popUpTo/saveState dance) so the back button returns to
// whichever tab was showing, and launchSingleTop keeps repeated taps from stacking copies
internal fun NavGraphBuilder.tabDestinations(navController: NavHostController) {
    val openSettings = { navController.navigateFromTap(KrateDestination.SETTINGS.route) }
    composable(KrateDestination.HOME.route) { entry ->
        TabScreen(
            title = stringResource(KrateDestination.HOME.titleRes),
            navController = navController,
            entry = entry,
            titleIcon = painterResource(R.drawable.ic_krate),
            subtitle = rememberKrateGreeting(),
            actions = {
                HomeChangelogAction()
                SettingsShortcutAction(onClick = openSettings)
            },
        ) {
            HomeScreen(
                onNavigateToApps = { navController.navigateToTab(KrateDestination.APPS.route) },
                onNavigateToUpdates = { navController.navigateToTab(KrateDestination.UPDATES.route) },
                onNavigateToApp = { appId -> navController.navigateFromTap("apps/$appId") },
            )
        }
    }
    composable(KrateDestination.APPS.route) { entry ->
        TabScreen(
            title = stringResource(KrateDestination.APPS.titleRes),
            navController = navController,
            entry = entry,
            subtitle = rememberAppsSubtitle(),
            actions = { SettingsShortcutAction(onClick = openSettings) },
        ) {
            AppsScreen(onAppClick = { appId -> navController.navigateFromTap("apps/$appId") })
        }
    }
    composable(KrateDestination.UPDATES.route) { entry ->
        TabScreen(
            title = stringResource(KrateDestination.UPDATES.titleRes),
            navController = navController,
            entry = entry,
            subtitle = rememberUpdatesSubtitle(),
            actions = { SettingsShortcutAction(onClick = openSettings) },
        ) {
            UpdatesScreen(onAppClick = { appId -> navController.navigateFromTap("apps/$appId") })
        }
    }
}

// Settings is reached via the shortcut above, not a bottom-nav tab, so it's pushed and popped like
// the App Details route - same transitions, same back affordance - rather than a directional tab slide
internal fun NavGraphBuilder.settingsDestination(navController: NavHostController) {
    composable(
        route = KrateDestination.SETTINGS.route,
        enterTransition = { detailsEnterTransition() },
        exitTransition = { detailsExitTransition() },
        popEnterTransition = { detailsPopEnterTransition() },
        popExitTransition = { detailsPopExitTransition() },
    ) { entry ->
        DetailScreen(
            title = stringResource(KrateDestination.SETTINGS.titleRes),
            navController = navController,
            entry = entry,
            onBack = { navController.popBackFromTap() },
        ) { scrollState, topContentPadding ->
            SettingsScreen(
                scrollState = scrollState,
                topContentPadding = topContentPadding,
                onNavigate = { route -> navController.navigateFromTap(route) },
            )
        }
    }
}

internal fun NavGraphBuilder.appearanceDestination(navController: NavHostController) {
    settingsPageDestination(navController, APPEARANCE_ROUTE, "Appearance") { scrollState, topContentPadding ->
        AppearanceRoute(
            scrollState = scrollState,
            topContentPadding = topContentPadding,
            onOpenCornerRadius = { navController.navigateFromTap(CORNER_RADIUS_ROUTE) },
        )
    }
    // full screen with its own back and Done, so the live bar preview can sit where the real bar does
    composable(
        route = CORNER_RADIUS_ROUTE,
        enterTransition = { detailsEnterTransition() },
        exitTransition = { detailsExitTransition() },
        popEnterTransition = { detailsPopEnterTransition() },
        popExitTransition = { detailsPopExitTransition() },
    ) {
        CornerRadiusEditor(onClose = { navController.popBackFromTap() })
    }
}

// the pages behind Settings' category rows - pushed and popped like App Details
internal fun NavGraphBuilder.settingsPageDestinations(navController: NavHostController) {
    settingsPageDestination(navController, SettingsPageRoute.DOWNLOADS, "Downloads & storage") { scroll, top ->
        DownloadsStoragePage(scrollState = scroll, topContentPadding = top)
    }
    settingsPageDestination(navController, SettingsPageRoute.BACKUPS, "Backups") { scroll, top ->
        BackupsPage(scrollState = scroll, topContentPadding = top)
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
            onBack = { navController.popBackFromTap() },
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
            onBack = { navController.popBackFromTap() },
        ) { scrollState, topContentPadding ->
            AppDetailsScreen(
                appId = appId,
                onNavigateToApp = { dependencyId -> navController.navigateFromTap("apps/$dependencyId") },
                scrollState = scrollState,
                topContentPadding = topContentPadding,
            )
        }
    }
}
