package dev.cl0ud9.krate.ui.onboarding

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.krate.domain.repository.SettingsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS = "setup_progress"
private const val KEY_STEP = "step"
private const val KEY_DARK_APPLIED = "dark_applied"

// first-run setup; progress lives in app storage, not saved screen state, so clearing Krate's data always restarts it
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    context: Context,
) : ViewModel() {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var stepIndex by mutableIntStateOf(prefs.getInt(KEY_STEP, 0))
        private set

    var showRadiusEditor by mutableStateOf(false)

    fun goToStep(step: Int) {
        stepIndex = step
        prefs.edit().putInt(KEY_STEP, step).apply()
    }

    // Krate starts dark, the recommended look, once per setup - a later choice on the theme step is left alone
    fun applyDarkOnce(apply: () -> Unit) {
        if (prefs.getBoolean(KEY_DARK_APPLIED, false)) return
        apply()
        prefs.edit().putBoolean(KEY_DARK_APPLIED, true).apply()
    }

    // saved first, and seen through even if this screen goes away meanwhile; the progress is only dropped after
    fun completeOnboarding() {
        viewModelScope.launch {
            withContext(NonCancellable) { settingsRepository.setOnboardingCompleted() }
            prefs.edit().clear().apply()
        }
    }
}
