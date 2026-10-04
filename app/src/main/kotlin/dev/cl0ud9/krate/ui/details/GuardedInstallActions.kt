package dev.cl0ud9.krate.ui.details

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.AutoBackup
import dev.cl0ud9.krate.domain.model.SetupKind
import dev.cl0ud9.krate.domain.model.SetupStep
import dev.cl0ud9.krate.platform.backup.AutoBackupRunner
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.BackupMode
import dev.cl0ud9.krate.platform.setup.fallbackIntent
import dev.cl0ud9.krate.platform.setup.stepIntent
import dev.cl0ud9.krate.ui.components.KrateDialog
import dev.cl0ud9.krate.ui.components.KrateDialogHeader

// the install actions, with a stop first for the ones that erase the app's data when the app has its own backup
internal class GuardedInstallActions(
    val install: () -> Unit,
    val retryFromScratch: () -> Unit,
)

@Composable
internal fun rememberBackupGuard(
    state: AppDetailsUiState,
    onInstall: () -> Unit,
    onRetryFromScratch: () -> Unit,
): GuardedInstallActions {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val backup =
        state.app.guide
            ?.backup
            .takeIf { state.installed != null }
    val action = pending
    val guide = state.app.guide
    val spec = guide?.autoBackup
    val autoOn = rememberAutoBackupOn()
    // the backup sheet, opened from the dialog; the dialog comes back once it closes, switched to automatic if that
    // was just turned on
    var sheet by remember { mutableStateOf(false) }
    if (action != null && backup != null && guide != null) {
        val onContinue = {
            pending = null
            action()
        }
        when {
            sheet -> KeepSettingsSheet(app = state.app, guide = guide, onDismiss = { sheet = false })
            spec != null && autoOn ->
                AutoBackupFirstDialog(app = state.app, spec = spec, onContinue = onContinue, onDismiss = {
                    pending =
                        null
                })
            else ->
                BackupFirstDialog(
                    app = state.app,
                    backup = backup,
                    onContinue = onContinue,
                    onDismiss = { pending = null },
                    onBackUp = if (spec != null) ({ sheet = true }) else null,
                )
        }
    }
    return GuardedInstallActions(
        install = { if (backup != null && state.installsFromScratch) pending = onInstall else onInstall() },
        retryFromScratch = { if (backup != null) pending = onRetryFromScratch else onRetryFromScratch() },
    )
}

@Composable
private fun BackupFirstDialog(
    app: AppProfile,
    backup: String,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    // opens the backup sheet, for an app Krate can back up itself once that's switched on
    onBackUp: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val body =
        if (onBackUp != null) {
            "This erases ${app.displayName}'s data on this phone, settings included. Krate can back them up for you " +
                "first and put them back after, or you can save them by hand."
        } else {
            "This erases ${app.displayName}'s data on this phone, settings included. To keep them, save a " +
                "backup first: $backup. You can bring it back once the new version is on."
        }
    KrateDialog(
        header =
            KrateDialogHeader(
                tag = "Before it goes ahead",
                icon = rememberVectorPainter(Icons.Filled.SettingsBackupRestore),
                title = "Back up first?",
                body = body,
            ),
        onDismissRequest = onDismiss,
        footer = {
            if (onBackUp != null) {
                TextButton(onClick = onBackUp) { Text("Back up first") }
            } else {
                launchIntent(context, app)?.let { intent ->
                    TextButton(onClick = { startSafely(context, intent, null) }) { Text("Open app") }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onContinue) { Text("Continue") }
        },
    )
}

// Krate saves the settings itself before going ahead, then carries on once they're safe
@Composable
private fun AutoBackupFirstDialog(
    app: AppProfile,
    spec: AutoBackup,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    var started by remember { mutableStateOf(false) }
    val state = autoBackupStateFor(app.packageName)
    LaunchedEffect(state, started) {
        if (started && state is AutoBackupState.Done && state.mode == BackupMode.SAVE) {
            AutoBackupRunner.acknowledge()
            onContinue()
        }
    }
    val failed = (state as? AutoBackupState.Failed)?.takeIf { started }
    val running = started && state is AutoBackupState.Running
    KrateDialog(
        header =
            KrateDialogHeader(
                tag = "Before it goes ahead",
                icon = rememberVectorPainter(Icons.Filled.SettingsBackupRestore),
                title = if (failed != null) "The backup didn't finish" else "Back up first?",
                body =
                    when {
                        failed != null -> "${failed.reason} You can try again, or go ahead without a backup."
                        running -> runningText(BackupMode.SAVE)
                        else ->
                            "This erases ${app.displayName}'s data on this phone, settings included. Krate backs " +
                                "them up first and puts them back once the new version is on."
                    },
            ),
        onDismissRequest = { if (!running) onDismiss() },
        footer = {
            if (!running) {
                TextButton(onClick = onContinue) { Text(if (failed != null) "Go ahead anyway" else "Skip") }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = { started = AutoBackupRunner.save(app.packageName, spec) }) {
                    Text(if (failed != null) "Try again" else "Back up and continue")
                }
            }
        },
    )
}

internal fun actionLabel(kind: SetupKind): String =
    when (kind) {
        SetupKind.ACCESSIBILITY -> "Turn on"
        SetupKind.IN_APP -> "Open app"
        else -> "Allow"
    }

internal fun openStep(
    context: Context,
    app: AppProfile,
    step: SetupStep,
) {
    val intent = stepIntent(app.packageName, step) ?: launchIntent(context, app) ?: return
    startSafely(context, intent, fallbackIntent(app.packageName, step))
}

internal fun launchIntent(
    context: Context,
    app: AppProfile,
): Intent? = context.packageManager.getLaunchIntentForPackage(app.packageName)

// some phones leave out a settings screen; the more general one still gets the person most of the way
internal fun startSafely(
    context: Context,
    intent: Intent,
    fallback: Intent?,
) {
    val opened = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (!opened &&
        fallback != null
    ) {
        runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
