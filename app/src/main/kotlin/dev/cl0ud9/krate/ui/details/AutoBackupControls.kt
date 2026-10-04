package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.AutoBackup
import dev.cl0ud9.krate.platform.backup.AppBackups
import dev.cl0ud9.krate.platform.backup.AutoBackupRunner
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.BackupMode
import dev.cl0ud9.krate.platform.setup.appInfoIntent
import dev.cl0ud9.krate.ui.components.ButtonRow
import dev.cl0ud9.krate.ui.theme.ShapeCache

// whether automatic backups are switched on, re-read each time Krate comes back from Android's settings
@Composable
internal fun rememberAutoBackupOn(): Boolean {
    val context = LocalContext.current
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    return remember(checks) { AutoBackupRunner.isOn(context) }
}

// this app's backup or restore, if one is running or just finished
@Composable
internal fun autoBackupStateFor(packageName: String): AutoBackupState? {
    val state by AutoBackupRunner.state.collectAsStateWithLifecycle()
    return when (val current = state) {
        is AutoBackupState.Running -> current.takeIf { it.packageName == packageName }
        is AutoBackupState.Done -> current.takeIf { it.packageName == packageName }
        is AutoBackupState.Failed -> current.takeIf { it.packageName == packageName }
        AutoBackupState.Idle -> null
    }
}

// the automatic way: switch it on once, then back up or restore with a tap; Krate also does both around a reinstall
@Composable
internal fun AutoBackupPanel(
    app: AppProfile,
    spec: AutoBackup,
) {
    val context = LocalContext.current
    val on = rememberAutoBackupOn()
    val state = autoBackupStateFor(app.packageName)
    val saved = remember(state, on) { AppBackups.read(context, app.packageName) }
    Surface(shape = ShapeCache.rounded16, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Automatic backup",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (!on) {
                TurnOnAutoBackup()
                return@Column
            }
            PanelText(panelText(state, saved?.savedAtMillis))
            if (state !is AutoBackupState.Running) {
                ButtonRow {
                    if (saved != null) {
                        TextButton(
                            onClick = { AutoBackupRunner.restore(context, app.packageName, spec) },
                        ) { Text("Restore") }
                    }
                    Button(onClick = { AutoBackupRunner.save(app.packageName, spec) }) { Text("Back up now") }
                }
            }
        }
    }
}

// whether automatic backups are on, for places about Krate as a whole rather than one app: how to switch them on, or
// that they're on and how to switch them off
@Composable
internal fun AutoBackupSwitchPanel(on: Boolean) {
    val context = LocalContext.current
    Surface(shape = ShapeCache.rounded16, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (on) "Automatic backups are on" else "Automatic backups",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (on) {
                PanelText(
                    "Krate backs up an app's settings before a reinstall from scratch and puts them back after. To " +
                        "stop, turn off Krate automatic backups in Android's accessibility settings.",
                )
                ButtonRow {
                    TextButton(onClick = { startSafely(context, AutoBackupRunner.settingsIntent(context), null) }) {
                        Text("Accessibility settings")
                    }
                }
            } else {
                TurnOnAutoBackup()
            }
        }
    }
}

// what it does, that it asks for accessibility access, and the way past Android holding that back for Krate
@Composable
private fun TurnOnAutoBackup() {
    val context = LocalContext.current
    var restrictedHelp by rememberSaveable { mutableStateOf(false) }
    PanelText(
        "Krate can do this for you: it opens the app, goes to its backup screen and keeps a copy here, then puts it " +
            "back after a reinstall. It needs Android's accessibility access, and only uses it while a backup runs.",
    )
    ButtonRow {
        TextButton(onClick = { restrictedHelp = !restrictedHelp }) { Text("Switch greyed out?") }
        Button(onClick = { startSafely(context, AutoBackupRunner.settingsIntent(context), null) }) { Text("Turn on") }
    }
    if (restrictedHelp) {
        PanelText(
            "Android holds this back for apps from outside an app store. Open Krate's App info, tap ⋮ at the top " +
                "right, choose Allow restricted settings, then turn it on.",
        )
        FilledTonalButton(onClick = { startSafely(context, appInfoIntent(context.packageName), null) }) {
            Text("Open Krate's App info")
        }
    }
}

@Composable
private fun PanelText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

private fun panelText(
    state: AutoBackupState?,
    savedAtMillis: Long?,
): String =
    when {
        state is AutoBackupState.Running -> runningText(state.mode)
        state is AutoBackupState.Failed -> state.reason
        state is AutoBackupState.Done && state.mode == BackupMode.RESTORE -> "Your settings are back."
        state is AutoBackupState.Done && state.nothingToSave ->
            "Nothing to back up yet: its settings are all at their defaults."
        else -> savedText(savedAtMillis)
    }

private fun savedText(savedAtMillis: Long?): String {
    val last = savedAtMillis?.let { "Last backup ${relativeTime(it)}." } ?: "No backup yet."
    return "$last Krate also backs up before a reinstall from scratch and restores after it."
}

internal fun runningText(mode: BackupMode): String =
    when (mode) {
        BackupMode.SAVE -> "Backing up. Krate is going through the app's backup screen..."
        BackupMode.RESTORE -> "Putting your settings back..."
    }
