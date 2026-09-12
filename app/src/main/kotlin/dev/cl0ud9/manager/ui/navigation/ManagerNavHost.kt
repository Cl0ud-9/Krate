package dev.cl0ud9.manager.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
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
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.updates.UpdatesScreen

private const val APP_DETAILS_ROUTE = "apps/{appId}"
private const val APP_ID_ARG = "appId"
private const val FADE_DURATION_MS = 180
private const val TAB_ENTER_INITIAL_SCALE = 0.94f

// only the bottom bar lives at this shared level now - it doesn't transition per-route, it just
// shows/hides, so it has no reason to sit inside NavHost's animated content. the top bar used to
// live here too, but that was the actual bug behind "the header doesn't move with the back swipe":
// Navigation Compose 2.9's NavHost drives its enter/exit transitions from the live predictive-back
// gesture progress (the screen follows your finger in real time on Android 14+), and that progress
// is only ever wired up to composables INSIDE NavHost. A top bar hoisted out here never saw that
// progress at all, so it just sat frozen for the whole drag and snapped once the gesture settled -
// no transitionSpec on the outside could fix that, because the problem was never which animation
// played, it was that the header wasn't part of the animated subtree in the first place
@Composable
fun ManagerNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = ManagerDestination.entries.any { it.route == currentRoute }

    // contentWindowInsets defaults to WindowInsets.systemBars, which would reserve the status bar's
    // top inset here AND again inside every TabScreen/DetailScreen's own TopAppBar (that's the
    // default inset every M3 TopAppBar carries) - zeroing it out here leaves exactly one place
    // (each screen's own top bar) consuming it, instead of double-padding every screen's title down
    Scaffold(
        bottomBar = { if (showBottomBar) ManagerBottomBar(navController, currentRoute) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        ManagerNavGraph(navController, modifier = Modifier.padding(innerPadding))
    }
}

// shared by the bottom nav bar and any in-content shortcut to a tab (Home's "View updates" CTA) -
// preserves each tab's own back stack/scroll position (restoreState) instead of starting fresh
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// a floating pill instead of an edge-to-edge bar - the single most recognizable piece of the
// app's shell. Scaffold's default bottomBar (NavigationBar) carries its own navigationBars
// inset padding automatically; a bare Surface doesn't, so windowInsetsPadding is applied explicitly
// before the floating margin, otherwise the pill would sit under the gesture nav area on some devices
@Composable
private fun ManagerBottomBar(
    navController: NavHostController,
    currentRoute: String?,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        shape = ShapeCache.smoothPill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ManagerDestination.entries.forEach { destination ->
                val selected = currentRoute == destination.route
                ManagerNavigationBarItem(
                    selected = selected,
                    onClick = { navController.navigateToTab(destination.route) },
                    icon = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    label = stringResource(destination.labelRes),
                )
            }
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
        composable(ManagerDestination.HOME.route) {
            TabScreen(title = stringResource(ManagerDestination.HOME.titleRes)) {
                HomeScreen(onNavigateToUpdates = { navController.navigateToTab(ManagerDestination.UPDATES.route) })
            }
        }
        composable(ManagerDestination.APPS.route) {
            TabScreen(title = stringResource(ManagerDestination.APPS.titleRes)) {
                AppsScreen(onAppClick = { appId -> navController.navigate("apps/$appId") })
            }
        }
        composable(ManagerDestination.UPDATES.route) {
            TabScreen(title = stringResource(ManagerDestination.UPDATES.titleRes)) {
                UpdatesScreen(onAppClick = { appId -> navController.navigate("apps/$appId") })
            }
        }
        composable(ManagerDestination.SETTINGS.route) {
            TabScreen(title = stringResource(ManagerDestination.SETTINGS.titleRes)) {
                SettingsScreen()
            }
        }

        appDetailsDestination(navController)
    }
}

private fun NavGraphBuilder.appDetailsDestination(navController: NavHostController) {
    composable(
        route = APP_DETAILS_ROUTE,
        arguments = listOf(navArgument(APP_ID_ARG) { type = NavType.StringType }),
        enterTransition = { detailsEnterTransition() },
        exitTransition = { detailsExitTransition() },
        popEnterTransition = { detailsPopEnterTransition() },
        popExitTransition = { detailsPopExitTransition() },
    ) { backStackEntry ->
        val appId = backStackEntry.arguments?.getString(APP_ID_ARG).orEmpty()
        DetailScreen(title = "App Details", onBack = { navController.popBackStack() }) {
            AppDetailsScreen(
                appId = appId,
                onNavigateToApp = { dependencyId -> navController.navigate("apps/$dependencyId") },
            )
        }
    }
}

// a tab destination's own top bar + opaque content, now composed as one subtree INSIDE NavHost so
// it rides the exact same enter/exit transition (and the same live predictive-back progress) as the
// content below it, instead of being hoisted out where no transition could ever reach it.
// transparent instead of a tonal-elevated bar - no boxed top chrome at all,
// just text/icons floating directly on the background, which reads as lighter than a filled app bar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabScreen(
    title: String,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.surface,
        ) {
            content()
        }
    }
}

// same reasoning as TabScreen, with a back affordance instead of a static title - styled as its own
// floating circular surface (matching the app's other circular icon buttons) instead of a plain
// borderless IconButton, so it reads as a control sitting on the page rather than part of a bar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 4.dp).size(40.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.surface,
        ) {
            content()
        }
    }
}
