package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.repository.AutoUpdateStore
import dev.cl0ud9.krate.domain.repository.SettingsRepository
import dev.cl0ud9.krate.domain.repository.buildKey
import dev.cl0ud9.krate.platform.autoupdate.AUTO_UPDATE_DELAY_MILLIS
import dev.cl0ud9.krate.platform.autoupdate.autoUpdatesSupported
import dev.cl0ud9.krate.platform.autoupdate.krateOwnsUpdates
import dev.cl0ud9.krate.ui.components.KrateSwitch
import dev.cl0ud9.krate.ui.components.SectionHeader
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.krateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private const val STOP_TIMEOUT_MS = 5000L

// this app's say in automatic updates; null while they're off in Settings
internal data class AutoUpdateChoice(
    val manual: Boolean,
    val skippedBuild: String?,
)

class AutoUpdateCardViewModel(
    settingsRepository: SettingsRepository,
    private val autoUpdateStore: AutoUpdateStore,
    private val appId: String,
) : ViewModel() {
    internal val choice: StateFlow<AutoUpdateChoice?> =
        combine(
            settingsRepository.observeAutoInstallUpdates(),
            settingsRepository.observeAutomaticDownloads(),
            autoUpdateStore.observeChoices(),
        ) { autoInstall, downloads, choices ->
            AutoUpdateChoice(appId in choices.manual, choices.skipped[appId])
                .takeIf { autoUpdatesSupported && autoInstall && downloads }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun setAutomatic(automatic: Boolean) {
        viewModelScope.launch { autoUpdateStore.setManual(appId, !automatic) }
    }

    // null takes the skip back
    fun skip(buildKey: String?) {
        viewModelScope.launch { autoUpdateStore.skip(appId, buildKey) }
    }
}

// shown under the action for an installed app while automatic updates are on
@Composable
internal fun AutoUpdateCard(state: AppDetailsUiState) {
    val viewModel = krateViewModel { AutoUpdateCardViewModel(it.settingsRepository, it.autoUpdateStore, state.app.id) }
    val choice by viewModel.choice.collectAsStateWithLifecycle()
    val current = choice ?: return
    val context = LocalContext.current
    // Krate becomes the installer of record once it installs or updates the app itself
    val krateOwns by produceState<Boolean?>(null, state.installed) {
        value = withContext(Dispatchers.IO) { krateOwnsUpdates(context, state.app.packageName) }
    }
    val owns = krateOwns ?: return
    // the switch is the user's say for this app; whether Krate can act on it right now is the line below it
    val allowed = state.app.installationMode != InstallationMode.CLEAN_INSTALL
    val pending = state.pendingUpdate
    // a skip only means something for an update Krate would otherwise install by itself
    val willUpdate = allowed && owns && !current.manual
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    title = "Update automatically",
                    icon = painterResource(R.drawable.ic_update_rounded),
                    modifier = Modifier.weight(1f),
                )
                KrateSwitch(
                    checked = allowed && !current.manual,
                    onCheckedChange = viewModel::setAutomatic,
                    enabled = allowed,
                )
            }
            Text(
                text = autoUpdateLine(state, owns, current, pending),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (willUpdate && pending != null) {
                val skipped = current.skippedBuild == pending.buildKey
                TextButton(onClick = { viewModel.skip(if (skipped) null else pending.buildKey) }) {
                    Text(if (skipped) "Undo skip" else "Skip this version")
                }
            }
        }
    }
}

private fun autoUpdateLine(
    state: AppDetailsUiState,
    krateOwns: Boolean,
    choice: AutoUpdateChoice,
    pending: ArtifactInfo?,
): String {
    val dueAt = pending?.publishedAtMillis?.plus(AUTO_UPDATE_DELAY_MILLIS)
    return when {
        state.app.installationMode == InstallationMode.CLEAN_INSTALL ->
            "This app always reinstalls from scratch, which erases its data, so Krate never updates it for you."
        choice.manual -> "Off for this app. Its updates wait for you."
        // a build that isn't one of Krate's (an app's own update option can put one on): replacing it would undo that
        !krateOwns && state.isDiverged ->
            "${state.app.displayName} is on ${state.installedVersionName}, a build that didn't come from Krate, " +
                "so Krate won't replace it by itself. New versions still show up in Updates for you to install."
        !krateOwns ->
            "This copy was installed outside Krate, so Android wants your OK for its next update. Krate tells " +
                "you when it's ready, and once you've installed it from here, updates are automatic again."
        pending == null -> "New versions install a day after release, while you're not using the app."
        choice.skippedBuild == pending.buildKey -> "Skipping ${pending.versionName}. Newer versions still install."
        dueAt != null && dueAt > System.currentTimeMillis() ->
            "${pending.versionName} installs after ${formatWhen(dueAt)}, once you're not using the app."
        else -> "${pending.versionName} installs soon, once you're not using the app."
    }
}

private fun formatWhen(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
