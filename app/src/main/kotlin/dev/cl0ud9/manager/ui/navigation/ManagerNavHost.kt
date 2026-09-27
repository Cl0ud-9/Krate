package dev.cl0ud9.manager.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.cl0ud9.manager.data.settings.DEFAULT_NAV_BAR_CORNER_RADIUS
import dev.cl0ud9.manager.domain.model.LaunchTab
import dev.cl0ud9.manager.domain.model.NavBarStyle
import dev.cl0ud9.manager.platform.appContainer
import dev.cl0ud9.manager.ui.components.LocalNavBarClearance
import dev.cl0ud9.manager.ui.components.NavBarFade
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.theme.rememberHeroGradient
import dev.cl0ud9.manager.ui.util.LocalScrollClaim
import dev.cl0ud9.manager.ui.util.ScrollClaim
import dev.cl0ud9.manager.ui.util.isShortScreen
import dev.cl0ud9.manager.ui.util.resetsScrollClaim

internal const val APPEARANCE_ROUTE = "settings/appearance"

// NavHost itself always starts at Home synchronously (ManagerNavHost below) - this applies the
// user's real preferred tab ONE time, once the DataStore read resolves, tracked by a rememberSaveable
// flag so a later recreation (process death while the app was, say, on App Details) never re-fires
// this and clobbers whatever backstack NavController itself already restored. hasPendingRoute lets a
// notification-tap deep link (ApplyPendingRoute below) win over this: without it, a cold start via
// notification on a phone with a non-Home default launch tab could navigate to the deep-linked app
// screen first, then get yanked back to the preferred tab the instant the DataStore read finishes a
// moment later - two competing first-navigations racing, deep link should always take priority
@Composable
private fun ApplyDefaultLaunchTabOnce(
    navController: NavHostController,
    defaultLaunchTab: LaunchTab?,
    hasPendingRoute: Boolean,
) {
    var applied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(defaultLaunchTab, hasPendingRoute) {
        if (applied) return@LaunchedEffect
        if (hasPendingRoute) {
            applied = true
            return@LaunchedEffect
        }
        val tab = defaultLaunchTab ?: return@LaunchedEffect
        applied = true
        val route = tab.toRoute()
        if (route != ManagerDestination.HOME.route) {
            navController.navigate(route) {
                popUpTo(ManagerDestination.HOME.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

// a notification tap (download-complete, update-available, Update All result) carries a target
// route - re-fires on every new non-null value (not gated to once), since each future notification
// tap should navigate again, not just the first one this composition ever sees
@Composable
private fun ApplyPendingRoute(
    navController: NavHostController,
    pendingRoute: String?,
    onRouteHandled: () -> Unit,
) {
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        navController.navigate(route) { launchSingleTop = true }
        onRouteHandled()
    }
}

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
fun ManagerNavHost(
    navController: NavHostController = rememberNavController(),
    pendingRoute: String? = null,
    onRouteHandled: () -> Unit = {},
) {
    ApplyPendingRoute(navController, pendingRoute, onRouteHandled)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = ManagerBottomNavDestinations.any { it.route == currentRoute }

    // Settings > Appearance's toggles - read directly here rather than through a
    // ManagerNavHost-specific ViewModel, matching the same lightweight pattern AppRoot already uses
    // for the onboarding-completed flag in KrateActivity.kt
    val context = LocalContext.current
    val settingsRepository = remember(context) { context.appContainer().settingsRepository }
    // starts from the saved values, so a cold start or rotation never shows the bar morphing from a default
    val saved = remember(settingsRepository) { settingsRepository.currentSettings() }
    val navBarStyle by settingsRepository.observeNavBarStyle().collectAsStateWithLifecycle(
        initialValue = saved?.navBarStyle ?: NavBarStyle.FLOATING_PILL,
    )
    val navBarCornerRadius by
        settingsRepository.observeNavBarCornerRadius().collectAsStateWithLifecycle(
            initialValue = saved?.navBarCornerRadius ?: DEFAULT_NAV_BAR_CORNER_RADIUS,
        )
    val navBarCompactMode by settingsRepository.observeNavBarCompactMode().collectAsStateWithLifecycle(
        initialValue = saved?.navBarCompactMode ?: false,
    )
    val disableBlur by
        settingsRepository.observeDisableBlur().collectAsStateWithLifecycle(initialValue = saved?.disableBlur ?: false)
    // NavHost always starts at Home immediately (see ManagerNavGraph below) rather than waiting on
    // this DataStore read - gating NavHost's own existence on it meant every cold start (including
    // reopening from Recents after Android killed the process mid-download) hit a real frame-or-more
    // gap with no NavHost/NavController in the tree at all, before this app ever had "always land on
    // Home instead of resuming" reported against it. Once this loads, applyDefaultLaunchTab below
    // redirects ONE time on a genuinely fresh launch, tracked by appliedDefaultLaunchTab so a
    // restored backstack from a previous session (e.g. still on an App Details page) is never
    // clobbered by this running again after process death
    val defaultLaunchTab by
        settingsRepository.observeDefaultLaunchTab().collectAsStateWithLifecycle(initialValue = saved?.defaultLaunchTab)
    ApplyDefaultLaunchTabOnce(navController, defaultLaunchTab, hasPendingRoute = pendingRoute != null)

    // continuous progress rather than a plain boolean - lets the bar slide fully off/on screen
    // instead of popping in and out the instant a route change flips showBottomBar. Tracks the
    // exact same duration/easing as the detail screen's own push/pop transition (DetailTransitions.kt)
    // rather than an independently-picked number - two animations racing to different finish lines
    // is what read as rushed/uncoordinated rather than one fluid motion
    val navBarVisibility by animateFloatAsState(
        targetValue = if (showBottomBar) 1f else 0f,
        animationSpec = tween(DETAIL_TRANSITION_MS, easing = M3EmphasizedEasing),
        label = "NavBarVisibility",
    )
    // stays composed for the whole slide-out, not just while showBottomBar is true, otherwise the
    // bar would be yanked out of the tree mid-animation instead of finishing its slide
    val renderBottomBar = showBottomBar || navBarVisibility > NAV_BAR_VISIBILITY_EPSILON

    // measured by the bar itself, only to slide it off screen by its own height
    val barHeightPx = remember { mutableIntStateOf(0) }
    // tabs run behind the bar and pad their own lists by its footprint, so the bar floats over the content
    // the shorter, icon-only bar when chosen for the pill, and always on a phone on its side
    val barCompact = isShortScreen() || (navBarCompactMode && navBarStyle == NavBarStyle.FLOATING_PILL)
    val navBarClearance = navBarFootprint(barCompact)

    CompositionLocalProvider(LocalDisableBlur provides disableBlur, LocalNavBarClearance provides navBarClearance) {
        // contentWindowInsets defaults to WindowInsets.systemBars, which would reserve the status
        // bar's top inset here AND again inside every TabScreen/DetailScreen's own TopAppBar (that's
        // the default inset every M3 TopAppBar carries) - zeroing it out here leaves exactly one
        // place (each screen's own top bar) consuming it, instead of double-padding every title down
        Scaffold(
            bottomBar = {
                if (renderBottomBar) {
                    ManagerBottomBar(
                        navController,
                        currentRoute,
                        NavBarAppearance(navBarStyle, navBarCornerRadius, barCompact),
                        navBarVisibility,
                        barHeightPx,
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            ManagerNavGraph(
                navController = navController,
                startDestination = ManagerDestination.HOME.route,
                modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            )
        }
    }
}

// shared by the bottom nav bar and any in-content shortcut to a tab (Home's "View updates" CTA) -
// preserves each tab's own back stack/scroll position (restoreState) instead of starting fresh
internal fun NavHostController.navigateToTab(route: String) {
    // re-selecting the tab already showing would pop and rebuild it, replaying its entrance
    if (currentBackStackEntry?.destination?.route == route) return
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// bundles the three Settings > Appearance nav-bar toggles into one param, purely to keep
// ManagerBottomBar under detekt's parameter-count threshold without losing each value's own name
// at the call site - cornerRadius only applies to FLOATING_PILL; compactMode is the resolved one, style included
internal data class NavBarAppearance(
    val style: NavBarStyle,
    val cornerRadius: Int,
    val compactMode: Boolean,
)

private const val NAV_BAR_VISIBILITY_EPSILON = 0.001f

// the landscape camera cutout and a sideways 3-button nav bar; content stays clear, backgrounds don't
internal val SideInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

@Composable
private fun ManagerNavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // tab-to-tab: slide directionally by index. anything else (the App Details push, or the
        // first frame with no "from" side yet) falls back to a plain fade+grow
        enterTransition = { tabEnterTransition(initialState.destination.route, targetState.destination.route) },
        exitTransition = { tabExitTransition(initialState.destination.route, targetState.destination.route) },
    ) {
        tabDestinations(navController)
        settingsDestination(navController)
        appearanceDestination(navController)
        settingsPageDestinations(navController)
        appDetailsDestination(navController)
    }
}

// a tab destination's own top bar + opaque content, now composed as one subtree INSIDE NavHost so
// it rides the exact same enter/exit transition (and the same live predictive-back progress) as the
// content below it, instead of being hoisted out where no transition could ever reach it.
// transparent instead of a tonal-elevated bar - no boxed top chrome at all, just text/icons
// floating directly on the background, which reads as lighter than a filled app bar
private val TabContentPanelRadius = 28.dp

// titleIcon and subtitle are Home's Krate mark and launch greeting - the other tabs are title-only
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnimatedContentScope.TabScreen(
    title: String,
    navController: NavHostController,
    entry: NavBackStackEntry,
    titleIcon: Painter? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val depth = rememberDepthEffect(navController, entry)
    // depth.contentModifier wraps the WHOLE screen - header strip and content panel together - not
    // just the panel, so a receding tab shrinks/blurs/rounds as one unified card, instead of
    // leaving the header a sharp, unaffected rectangle above it
    Box(modifier = Modifier.fillMaxSize().then(depth.contentModifier)) {
        // the tint wash only ever shows through the header strip - everything below sits on an
        // opaque, rounded-top panel starting right under it (ignoring the bottom inset, so the
        // panel's own color still shows through the gaps around the floating nav bar) - a
        // top-tinted, bottom-solid split instead of one wash bleeding all the way down
        Scaffold(
            modifier = Modifier.background(rememberHeroGradient()),
            topBar = {
                // content keeps clear of the side insets (landscape camera cutout); backgrounds run edge to edge
                Box(modifier = Modifier.windowInsetsPadding(SideInsets)) {
                    TabHeader(title = title, icon = titleIcon, subtitle = subtitle, actions = actions)
                }
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
                color = MaterialTheme.colorScheme.surface,
                shape = ShapeCache.contentPanel(TabContentPanelRadius),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(SideInsets)) { content() }
                    NavBarFade()
                }
            }
        }
        // skipped entirely at rest (isDimVisible == false) rather than always drawn transparent -
        // one less full-screen layer on every tab, every frame, while nothing is actually covering
        // it. alpha itself is read as depth.dimAlpha.value inside the layer lambda, not passed in
        // as a plain Float, so the fade doesn't force this whole composable to recompose every frame
        if (depth.isDimVisible) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = depth.dimAlpha.value }
                        .background(Color.Black),
            )
        }
    }
}

// same reasoning as TabScreen for the depth effect, but no tint wash and no static bar - detail
// screens are plain, and the heading here rides up with the content as the user scrolls instead
// of sitting fixed, fading its own background in only once collapsed
@Composable
internal fun AnimatedContentScope.DetailScreen(
    title: String,
    navController: NavHostController,
    entry: NavBackStackEntry,
    onBack: () -> Unit,
    content: @Composable (scrollState: ScrollState, topContentPadding: Dp) -> Unit,
) {
    val depth = rememberDepthEffect(navController, entry)
    val scrollState = rememberScrollState()
    val headerState = rememberCollapsingHeaderState(scrollState)
    val scrollClaim = remember { ScrollClaim() }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .resetsScrollClaim(scrollClaim)
                .nestedScroll(collapsingHeaderNestedScrollConnection(headerState, scrollState, scrollClaim))
                // background has to sit AFTER (inside) the depth effect's own graphicsLayer, not
                // before it - a draw modifier chained before a graphicsLayer paints to the layer
                // behind it, so it would never actually be clipped by that layer's rounded corners
                .then(depth.contentModifier)
                .background(MaterialTheme.colorScheme.surface),
    ) {
        CompositionLocalProvider(
            LocalScrollClaim provides scrollClaim,
        ) {
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(SideInsets)) {
                content(scrollState, headerState.headerHeight)
            }
        }
        CollapsingDetailHeader(title = title, state = headerState, onBack = onBack)
        if (depth.isDimVisible) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = depth.dimAlpha.value }
                        .background(Color.Black),
            )
        }
    }
}
