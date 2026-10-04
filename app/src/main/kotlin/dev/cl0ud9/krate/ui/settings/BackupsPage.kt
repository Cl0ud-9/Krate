package dev.cl0ud9.krate.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.platform.backup.AppBackups
import dev.cl0ud9.krate.platform.backup.AutoBackupRunner
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.ui.details.AutoBackupSwitchPanel
import dev.cl0ud9.krate.ui.details.autoBackupStateFor
import dev.cl0ud9.krate.ui.details.relativeTime
import dev.cl0ud9.krate.ui.details.rememberAutoBackupOn
import dev.cl0ud9.krate.ui.details.runningText

// automatic backups in one place: whether they're on, and for each app that supports them, when it was last saved
@Composable
fun BackupsPage(
    scrollState: ScrollState,
    topContentPadding: Dp,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val catalog by remember(container) {
        container.catalogRepository.observeApps()
    }.collectAsStateWithLifecycle(emptyList())
    val on = rememberAutoBackupOn()
    val supported = catalog.filter { it.guide?.autoBackup != null }
    SettingsPage(scrollState, topContentPadding) {
        AutoBackupSwitchPanel(on = on)
        SettingsSectionLabel("Apps that support it")
        if (supported.isEmpty()) {
            Text(
                text =
                    "None of the apps in your Krate support it yet. Apps that can export their settings as text " +
                        "show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        supported.forEach { app ->
            val reader = container.installedPackageReader
            val installed = remember(app.packageName) { reader.installedVersion(app.packageName) != null }
            BackupAppRow(app = app, installed = installed, on = on)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun BackupAppRow(
    app: AppProfile,
    installed: Boolean,
    on: Boolean,
) {
    val context = LocalContext.current
    val state = autoBackupStateFor(app.packageName)
    val saved = remember(state) { AppBackups.read(context, app.packageName) }
    val status =
        when {
            state is AutoBackupState.Running -> runningText(state.mode)
            state is AutoBackupState.Failed -> state.reason
            state is AutoBackupState.Done && state.nothingToSave -> "Nothing to back up yet"
            saved != null -> "Backed up ${relativeTime(saved.savedAtMillis)}"
            !installed -> "Not installed"
            else -> "Not backed up yet"
        }
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val spec = app.guide?.autoBackup?.takeIf { on && installed }
        if (spec != null && state !is AutoBackupState.Running) {
            TextButton(onClick = { AutoBackupRunner.save(app.packageName, spec) }) { Text("Back up") }
        }
    }
}
