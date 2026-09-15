package dev.cl0ud9.manager

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.domain.model.ThemeMode
import dev.cl0ud9.manager.platform.appContainer
import dev.cl0ud9.manager.ui.navigation.ManagerNavHost
import dev.cl0ud9.manager.ui.onboarding.OnboardingScreen
import dev.cl0ud9.manager.ui.theme.ManagerTheme
import dev.cl0ud9.manager.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsRepository = remember { context.appContainer().settingsRepository }
            val themeMode by
                settingsRepository.observeThemeMode().collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val useSmoothCorners by
                settingsRepository.observeUseSmoothCorners().collectAsStateWithLifecycle(initialValue = true)

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

            ManagerTheme(themeMode = themeMode, useSmoothCorners = useSmoothCorners) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

// "is_benchmark" intent extra, set only by the :baselineprofile module's own generator - never
// present on a real launch, so this is a no-op for every actual user. Lets baseline profile
// generation reach the real navigation flows directly instead of needing to drive onboarding first
private const val BENCHMARK_EXTRA = "is_benchmark"

// gates the Apps catalog behind first-run onboarding, amendment 44.4 of the spec - null while the
// DataStore read is still in flight, so a returning user is never shown a flash of onboarding they
// already completed
@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val isBenchmarkMode =
        remember { (context as? Activity)?.intent?.getBooleanExtra(BENCHMARK_EXTRA, false) == true }
    val settingsRepository = remember { context.appContainer().settingsRepository }
    val onboardingCompleted: Boolean? by
        settingsRepository.observeOnboardingCompleted().collectAsStateWithLifecycle(initialValue = null)

    when {
        isBenchmarkMode -> ManagerNavHost()
        onboardingCompleted == null -> Unit
        onboardingCompleted == false -> OnboardingScreen(onComplete = {})
        else -> ManagerNavHost()
    }
}
