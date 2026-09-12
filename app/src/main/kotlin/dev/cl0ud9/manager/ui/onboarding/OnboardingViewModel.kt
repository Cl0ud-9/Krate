package dev.cl0ud9.manager.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.domain.repository.SettingsRepository
import kotlinx.coroutines.launch

// first-run onboarding, amendment 44.4 of the spec - runs once, before the Apps catalog is reachable
class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted() }
    }
}
