package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import dev.cl0ud9.krate.platform.autoupdate.otherStoreName
import dev.cl0ud9.krate.ui.components.KrateSwitch
import dev.cl0ud9.krate.ui.components.SectionHeader
import dev.cl0ud9.krate.ui.navigation.glassRim
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
    val ownership by produceState<UpdateOwnership?>(
        null,
        state.installed,
        state.downloadStatus::class,
    ) {
        value =
            withContext(Dispatchers.IO) {
                UpdateOwnership(
                    krateOwns = krateOwnsUpdates(context, state.app.packageName),
                    otherStore = otherStoreName(context, state.app.packageName),
                )
            }
    }
    val owned = ownership ?: return
    val owns = owned.krateOwns
    // the switch is the user's say for this app; whether Krate can act on it right now is the line below it
    val allowed = state.app.installationMode != InstallationMode.CLEAN_INSTALL
    val pending = state.pendingUpdate
    // a skip only means something for an update Krate would otherwise install by itself
    val willUpdate = allowed && owns && !current.manual
    Card(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
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
            AutoUpdateStatus(autoUpdateLine(state, owned, current, pending))
            if (willUpdate && pending != null) {
                val skipped = current.skippedBuild == pending.buildKey
                TextButton(
                    onClick = { viewModel.skip(if (skipped) null else pending.buildKey) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (skipped) "Don't skip" else "Skip this version")
                }
            }
        }
    }
}

// what's happening with this app's updates, as a headline and one line of detail on an inset panel
private data class AutoUpdateLine(
    val icon: Int,
    val headline: String,
    val detail: String,
)

@Composable
private fun AutoUpdateStatus(line: AutoUpdateLine) {
    Surface(shape = ShapeCache.rounded12, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painterResource(line.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp).size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = line.headline, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = line.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// whether Krate may update the app by itself, and if not, which other store Android has down for it
private data class UpdateOwnership(
    val krateOwns: Boolean,
    val otherStore: String?,
)

private fun autoUpdateLine(
    state: AppDetailsUiState,
    ownership: UpdateOwnership,
    choice: AutoUpdateChoice,
    pending: ArtifactInfo?,
): AutoUpdateLine = whyByHand(state, ownership, choice) ?: pendingLine(state.app.displayName, choice, pending)

// the reasons an app can't update by itself at all, or null when it can
private fun whyByHand(
    state: AppDetailsUiState,
    ownership: UpdateOwnership,
    choice: AutoUpdateChoice,
): AutoUpdateLine? {
    val name = state.app.displayName
    val info = R.drawable.ic_info_rounded
    val krateOwns = ownership.krateOwns
    return when {
        state.app.installationMode == InstallationMode.CLEAN_INSTALL ->
            AutoUpdateLine(
                info,
                "Updates by hand only",
                "$name reinstalls from scratch to update, which erases its data, so Krate leaves that to you.",
            )
        choice.manual -> AutoUpdateLine(info, "Off for $name", "New versions wait in Updates until you install them.")
        // a build that isn't one of Krate's (an app's own update option can put one on): replacing it would undo that
        !krateOwns && state.isDiverged ->
            AutoUpdateLine(
                info,
                "Paused for this version",
                "You're on ${state.installedVersionName}, which didn't come from Krate, so Krate won't replace it. " +
                    "New versions still appear in Updates.",
            )
        !krateOwns && ownership.otherStore != null ->
            AutoUpdateLine(
                info,
                "Confirm one update first",
                "$name came from ${ownership.otherStore}, and Android only lets Krate update an app by itself once " +
                    "Krate has installed it. Tap Update or Reinstall above and confirm Android's prompt once. After " +
                    "that, Krate keeps it updated by itself.",
            )
        // nothing on record: most often Krate itself was reinstalled, which makes Android forget who installed what
        !krateOwns ->
            AutoUpdateLine(
                info,
                "Update it once here",
                "Update it once here and Krate can keep it updated by itself from then on. Android has no record " +
                    "of Krate installing $name (this happens after Krate is reinstalled, or when an app came from a " +
                    "file), so it asks you to confirm that one update. If it's already up to date, tap Reinstall " +
                    "instead.",
            )
        else -> null
    }
}

// for an app Krate updates by itself: what's next, if anything
private fun pendingLine(
    name: String,
    choice: AutoUpdateChoice,
    pending: ArtifactInfo?,
): AutoUpdateLine {
    val info = R.drawable.ic_info_rounded
    val clock = R.drawable.ic_update_rounded
    val dueAt = pending?.publishedAtMillis?.plus(AUTO_UPDATE_DELAY_MILLIS)
    return when {
        pending == null ->
            AutoUpdateLine(
                R.drawable.ic_check_circle_rounded,
                "On",
                "New versions install a day after release, while you're not using $name.",
            )
        // the person's own skip comes first; it's their word on this version
        choice.skippedBuild == pending.buildKey ->
            AutoUpdateLine(info, "Skipping ${pending.versionName}", "Newer versions will still install by themselves.")
        dueAt != null && dueAt > System.currentTimeMillis() ->
            AutoUpdateLine(
                clock,
                "${pending.versionName} is queued",
                "It installs after ${formatWhen(dueAt)}, at a moment when you're not using $name. Android picks the " +
                    "exact time to save battery.",
            )
        else -> AutoUpdateLine(clock, "${pending.versionName} installs soon", "As soon as you're not using $name.")
    }
}

private fun formatWhen(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
