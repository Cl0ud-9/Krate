package dev.cl0ud9.krate

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.cl0ud9.krate.domain.model.ThemeMode
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.platform.workers.KRATE_UPDATED_NOTIFICATION_ID
import dev.cl0ud9.krate.ui.components.KrateIntro
import dev.cl0ud9.krate.ui.components.KrateIntroProgress
import dev.cl0ud9.krate.ui.components.LocalIntroHeaderSlot
import dev.cl0ud9.krate.ui.components.LocalIntroPlaying
import dev.cl0ud9.krate.ui.navigation.ArrivalFromOutside
import dev.cl0ud9.krate.ui.navigation.KrateNavHost
import dev.cl0ud9.krate.ui.navigation.LiveNavigation
import dev.cl0ud9.krate.ui.onboarding.OnboardingScreen
import dev.cl0ud9.krate.ui.theme.KrateTheme
import dev.cl0ud9.krate.ui.theme.resolveDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val EXTRA_TARGET_ROUTE = "target_route"
const val EXTRA_APP_ID = "appId"
const val EXTRA_NOTIFICATION_ID = "notification_id"

class KrateActivity : ComponentActivity() {
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        lifecycleScope.launch {
            appContainer().settingsRepository.observeThemeMode().distinctUntilChanged().collect {
                applyLaunchNightMode(
                    it,
                )
            }
        }
        setContent {
            val context = LocalContext.current
            val settingsRepository = remember { context.appContainer().settingsRepository }
            // the saved theme from the first frame, so a rotation or reopen doesn't flash the system theme
            val saved = remember { settingsRepository.currentSettings() }
            val themeMode by settingsRepository.observeThemeMode().collectAsStateWithLifecycle(
                initialValue = saved?.themeMode ?: ThemeMode.SYSTEM,
            )
            val route by pendingRoute.collectAsStateWithLifecycle()

            // enableEdgeToEdge() alone only ever picks status/nav bar icon color from the raw system
            // dark-mode setting at launch, so an explicit in-app Light/Dark override (independent of
            // the system setting) left icons the wrong color on top of the resulting background
            val darkTheme = themeMode.resolveDarkTheme()
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }

            KrateTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        pendingRoute = route,
                        onRouteHandled = { pendingRoute.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // clears the extras off the Intent itself, not just this function's local read of them -
    // onNewIntent's setIntent(intent) below makes THIS Intent instance sticky for every future
    // onCreate() the system replays after killing this process in the background (real, documented
    // Android behavior, not hypothetical: a task's process can die at any point while backgrounded
    // since this app runs no foreground service, and reopening it from Recents/the launcher then
    // redelivers the same Intent that last updated the task - extras included). Without clearing
    // them here, a single notification tap would force every later cold start back to that same
    // deep-linked route permanently, with no way to actually reach Home again from the bottom nav
    // that redirect keeps winning against
    private fun handleIntent(intent: Intent?) {
        // Android only dismisses a notification on a tap of its body, not of its button, so Krate does it itself;
        // any int is a real id (a download's is based on its app's hash, often negative), so presence is the test
        if (intent?.hasExtra(EXTRA_NOTIFICATION_ID) == true) {
            NotificationManagerCompat.from(this).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
            intent.removeExtra(EXTRA_NOTIFICATION_ID)
        }
        // Krate is open now, which is all "Krate updated, tap to open" was asking for
        NotificationManagerCompat.from(this).cancel(KRATE_UPDATED_NOTIFICATION_ID)
        val route =
            intent?.getStringExtra(EXTRA_TARGET_ROUTE)
                ?: intent?.getStringExtra(EXTRA_APP_ID)?.let { "apps/$it" }
        if (!route.isNullOrEmpty()) {
            // a tap while Krate is already on screen keeps the normal push animation
            val fromOutside = !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (fromOutside) ArrivalFromOutside.arm()
            // already running: switch right now, before the window draws, so it opens straight onto the target
            if (!(fromOutside && LiveNavigation.open(route))) pendingRoute.value = route
            intent?.removeExtra(EXTRA_TARGET_ROUTE)
            intent?.removeExtra(EXTRA_APP_ID)
        }
    }
}

// the system draws the launch screen before our code runs, so it only follows an in-app Light/Dark choice once told
private fun Context.applyLaunchNightMode(mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val nightMode =
        when (mode) {
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        }
    getSystemService(UiModeManager::class.java).setApplicationNightMode(nightMode)
}

// "is_benchmark" intent extra, set only by the :baselineprofile module's own generator - never
// present on a real launch, so this is a no-op for every actual user. Lets baseline profile
// generation reach the real navigation flows directly instead of needing to drive onboarding first
private const val BENCHMARK_EXTRA = "is_benchmark"

// gates the Apps catalog behind first-run onboarding, amendment 44.4 of the spec - null while the
// DataStore read is still in flight, so a returning user is never shown a flash of onboarding they
// already completed
@Composable
private fun AppRoot(
    pendingRoute: String? = null,
    onRouteHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val isBenchmarkMode =
        remember { (context as? Activity)?.intent?.getBooleanExtra(BENCHMARK_EXTRA, false) == true }
    val settingsRepository = remember { context.appContainer().settingsRepository }
    val onboardingCompleted: Boolean? by
        settingsRepository.observeOnboardingCompleted().collectAsStateWithLifecycle(
            initialValue = remember { settingsRepository.currentSettings()?.onboardingCompleted },
        )

    // the catalog loads while setup is on screen, so Home first draws with its content behind the intro, not mid-flight
    LaunchedEffect(Unit) {
        runCatching {
            context
                .appContainer()
                .catalogRepository
                .observeApps()
                .first()
        }
    }

    // after setup: setup stays until the intro covers it, Home loads beneath, Home's dialogs wait for the landing
    val intro = remember { IntroState() }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isBenchmarkMode -> KrateNavHost(pendingRoute = pendingRoute, onRouteHandled = onRouteHandled)
            onboardingCompleted == null && !intro.started -> Unit
            // a finished setup counts from the tap, not from when storage catches up, which can lag by seconds
            (onboardingCompleted == false && !intro.started) || intro.holdsSetup ->
                OnboardingScreen(markHidden = intro.started, onComplete = intro::start)
            else ->
                CompositionLocalProvider(
                    LocalIntroPlaying provides intro.playing,
                    LocalIntroHeaderSlot provides intro.headerSlot,
                ) {
                    // a setup that just ran starts Home fresh rather than restoring navigation saved before it
                    key(intro.started) {
                        KrateNavHost(pendingRoute = pendingRoute, onRouteHandled = onRouteHandled)
                    }
                }
        }
        if (intro.started && !intro.finished) {
            KrateIntro(
                start = intro.markBounds,
                progress = intro.progress,
                onCovered = { intro.covered = true },
                onLanded = { intro.landed = true },
                onFinished = { intro.finished = true },
            )
        }
    }
}

// where the post-setup intro is; not saved, so a process restart mid-intro just lands on Home
private class IntroState {
    var started by mutableStateOf(false)
    var markBounds by mutableStateOf<Rect?>(null)
    var covered by mutableStateOf(false)
    var landed by mutableStateOf(false)
    var finished by mutableStateOf(false)

    val progress = KrateIntroProgress()

    val holdsSetup: Boolean get() = started && !covered
    val playing: Boolean get() = started && !landed
    val headerSlot: (() -> Float)? get() = if (started && !finished) progress::headerSlot else null

    fun start(bounds: Rect?) {
        if (started) return
        markBounds = bounds
        started = true
    }
}
