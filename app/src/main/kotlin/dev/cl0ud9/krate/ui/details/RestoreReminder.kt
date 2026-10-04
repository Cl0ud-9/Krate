package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.model.AppGuide
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.backup.AppBackups
import dev.cl0ud9.krate.platform.backup.AutoBackupRunner
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.BackupMode
import dev.cl0ud9.krate.ui.theme.ShapeCache

// after a reinstall that erased the app's data: Krate puts the saved settings back by itself when it can, otherwise
// says how
@Composable
internal fun RestoreReminder(
    app: AppProfile,
    guide: AppGuide,
) {
    val context = LocalContext.current
    val spec = guide.autoBackup
    val on = rememberAutoBackupOn()
    val state = autoBackupStateFor(app.packageName)
    val saved = remember { AppBackups.read(context, app.packageName) }
    val automatic = spec != null && on && saved != null
    var tried by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(automatic) {
        if (automatic && !tried && spec != null) {
            tried = true
            AutoBackupRunner.restore(context, app.packageName, spec)
        }
    }
    val restore = guide.restore
    if (!automatic && restore == null) return
    val text = restoreText(state, automatic, restore)
    Surface(shape = ShapeCache.rounded12, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Bring your settings back",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            val failed = state is AutoBackupState.Failed
            if (failed && spec != null) {
                FilledTonalButton(
                    onClick = { AutoBackupRunner.restore(context, app.packageName, spec) },
                ) { Text("Try again") }
            } else if (!automatic) {
                launchIntent(context, app)?.let { intent ->
                    FilledTonalButton(
                        onClick = { startSafely(context, intent, null) },
                    ) { Text("Open ${app.displayName}") }
                }
            }
        }
    }
}

private fun restoreText(
    state: AutoBackupState?,
    automatic: Boolean,
    restore: String?,
): String =
    when {
        state is AutoBackupState.Running -> runningText(state.mode)
        state is AutoBackupState.Done && state.mode == BackupMode.RESTORE -> "Your settings are back."
        state is AutoBackupState.Failed && state.mode == BackupMode.RESTORE ->
            "${state.reason} You can put them back by hand: $restore."
        automatic -> runningText(BackupMode.RESTORE)
        else -> "This was a fresh install, so it starts empty. If you saved a backup: $restore."
    }
