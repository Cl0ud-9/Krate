package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.isTracked

// the setup card and the auto-update card, which fold away as an uninstall starts and unfold after an install, instead
// of the page jumping
@Composable
internal fun InstalledCards(
    app: AppProfile,
    state: AppDetailsUiState,
    uninstalling: Boolean,
) {
    val guide = app.guide
    val onPhone = state.installed != null && !uninstalling
    AnimatedVisibility(visible = onPhone && guide != null, enter = CardIn, exit = CardOut) {
        guide?.let {
            SetupCard(
                app = app,
                guide = it,
                restorePending = state.restorePending,
                justInstalled = state.justInstalled,
            )
        }
    }
    AnimatedVisibility(visible = onPhone && guide == null && app.isTracked, enter = CardIn, exit = CardOut) {
        TrackedLinksCard(app)
    }
    AnimatedVisibility(visible = onPhone, enter = CardIn, exit = CardOut) { AutoUpdateCard(state) }
}
