package dev.cl0ud9.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.domain.model.ThemeMode
import dev.cl0ud9.manager.platform.appContainer
import dev.cl0ud9.manager.ui.navigation.ManagerNavHost
import dev.cl0ud9.manager.ui.onboarding.OnboardingScreen
import dev.cl0ud9.manager.ui.theme.ManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsRepository = remember { context.appContainer().settingsRepository }
            val themeMode by
                settingsRepository.observeThemeMode().collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            ManagerTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

// gates the Apps catalog behind first-run onboarding, amendment 44.4 of the spec - null while the
// DataStore read is still in flight, so a returning user is never shown a flash of onboarding they
// already completed
@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val settingsRepository = remember { context.appContainer().settingsRepository }
    val onboardingCompleted: Boolean? by
        settingsRepository.observeOnboardingCompleted().collectAsStateWithLifecycle(initialValue = null)

    when (onboardingCompleted) {
        null -> Unit
        false -> OnboardingScreen(onComplete = {})
        true -> ManagerNavHost()
    }
}
