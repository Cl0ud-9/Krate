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
    heldBy: String? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = if (on) "✓" else "•", color = MaterialTheme.colorScheme.primary)
        Column {
            Text(text = host, style = MaterialTheme.typography.bodyMedium)
            Text(
                text =
                    when {
                        heldBy != null -> "Opens in $heldBy now"
                        on -> "Opens here already"
                        else -> "Not switched on yet"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SheetNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// said before anything is done: an address another app holds only moves once that app's links are switched off, which
// moves all of that app's links to the browser
internal fun releaseNote(
    app: AppProfile,
    standing: LinkStanding,
): String? {
    val names = standing.releaseNames()
    if (names.isEmpty() || !OpenByDefault.supported) return null
    val who = names.joinToString(" and ")
    return "$who opens some of these now, and Android lets that app keep them. To give them to ${app.displayName}, " +
        "Krate switches off $who's own links in Android's settings first. All of its links then open in your " +
        "browser instead. Switch them back on its \"Open by default\" page whenever you like. To do it yourself: " +
        "open $who's page, switch off its links, then tick these on ${app.displayName}'s."
}

internal fun statusText(
    state: AutoBackupState?,
    standing: LinkStanding,
): String? =
    when {
        state is AutoBackupState.Running -> "Working on it in Android's settings..."
        state is AutoBackupState.Failed -> "${state.reason} Open the page and tick them yourself."
        state is AutoBackupState.Done && standing.todo.isNotEmpty() ->
            "Android didn't take them all. Open the page and tick the rest."
        state is AutoBackupState.Done -> "Done."
        !standing.readable && OpenByDefault.supported -> "Krate can't read this app's links on this phone."
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
