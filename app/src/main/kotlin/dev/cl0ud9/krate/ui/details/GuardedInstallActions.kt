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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.SetupKind
import dev.cl0ud9.krate.domain.model.SetupStep
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
    if (action != null && backup != null) {
        BackupFirstDialog(
            app = state.app,
            backup = backup,
            onContinue = {
                pending = null
                action()
            },
            onDismiss = { pending = null },
        )
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
) {
    val context = LocalContext.current
    KrateDialog(
        header =
            KrateDialogHeader(
                tag = "Before it goes ahead",
                icon = rememberVectorPainter(Icons.Filled.SettingsBackupRestore),
                title = "Back up first?",
                body =
                    "This erases ${app.displayName}'s data on this phone, settings included. To keep them, save a " +
                        "backup first: $backup. You can bring it back once the new version is on.",
            ),
        onDismissRequest = onDismiss,
        footer = {
            launchIntent(context, app)?.let { intent ->
                TextButton(onClick = { startSafely(context, intent, null) }) { Text("Open ${app.displayName}") }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onContinue) { Text("Continue") }
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
