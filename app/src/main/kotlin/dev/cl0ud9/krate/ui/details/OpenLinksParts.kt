package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.BackupMode
import dev.cl0ud9.krate.platform.links.OpenByDefault
import dev.cl0ud9.krate.platform.setup.appInfoIntent

internal fun introText(app: AppProfile): String =
    if (OpenByDefault.supported) {
        "Links to these addresses can open straight in ${app.displayName} instead of your browser or another app. " +
            "Krate only picks the ones that belong here."
    } else {
        "This version of Android has no switch for single addresses. Open ${app.displayName}'s page in Android's " +
            "settings and look for \"Open by default\"."
    }

@Composable
internal fun LinkLine(
    host: String,
    on: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = if (on) "✓" else "•", color = MaterialTheme.colorScheme.primary)
        Column {
            Text(text = host, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (on) "Opens here already" else "Not switched on yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun statusText(
    state: AutoBackupState?,
    helperOn: Boolean,
    standing: LinkStanding,
): String? =
    when {
        state is AutoBackupState.Running -> "Working on it in Android's settings..."
        state is AutoBackupState.Failed -> "${state.reason} Open the page and tick them yourself."
        state is AutoBackupState.Done && standing.todo.isNotEmpty() ->
            "Android didn't take them all. Open the page and tick the rest."
        state is AutoBackupState.Done -> "Done."
        !standing.readable && OpenByDefault.supported -> "Krate can't read this app's links on this phone."
        !helperOn -> "Turn on Krate's helper (in Backups) and Krate can tick them for you."
        else -> null
    }

internal fun AutoBackupState.isOpenLinksRun(): Boolean =
    when (this) {
        is AutoBackupState.Running -> mode == BackupMode.OPEN_LINKS
        is AutoBackupState.Done -> mode == BackupMode.OPEN_LINKS
        is AutoBackupState.Failed -> mode == BackupMode.OPEN_LINKS
        AutoBackupState.Idle -> false
    }

internal fun openPage(app: AppProfile) =
    if (OpenByDefault.supported) OpenByDefault.intent(app.packageName) else appInfoIntent(app.packageName)
