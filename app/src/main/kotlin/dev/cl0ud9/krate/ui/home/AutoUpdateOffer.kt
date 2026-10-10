package dev.cl0ud9.krate.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.repository.SettingsRepository
import dev.cl0ud9.krate.platform.autoupdate.autoUpdatesSupported
import dev.cl0ud9.krate.ui.components.ButtonRow
import dev.cl0ud9.krate.ui.components.SectionHeader
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.krateViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5000L

// asks once anyone who had Krate before automatic updates existed; either answer settles it for good
class AutoUpdateOfferViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    // undecided, on a phone that can, with the downloads it installs from switched on
    val visible: StateFlow<Boolean> =
        combine(
            settingsRepository.observeAutoInstallChosen(),
            settingsRepository.observeAutomaticDownloads(),
        ) { chosen, downloads -> autoUpdatesSupported && !chosen && downloads }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun answer(turnOn: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoInstallUpdates(turnOn) }
    }
}

@Composable
internal fun AutoUpdateOffer() {
    val viewModel = krateViewModel { AutoUpdateOfferViewModel(it.settingsRepository) }
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    if (!visible) return
    Card(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = "Updates that install themselves",
                icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text =
                    "Krate can now install updates for the apps you got from it, a day after each release and " +
                        "while you're not using them. It never rolls an app back or erases its data, and any " +
                        "app can stay manual. You can change this in Settings > Downloads & storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ButtonRow {
                TextButton(onClick = { viewModel.answer(turnOn = false) }) { Text("No thanks") }
                FilledTonalButton(onClick = { viewModel.answer(turnOn = true) }) { Text("Turn on") }
            }
        }
    }
}
