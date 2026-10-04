package dev.cl0ud9.krate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.platform.selfupdate.KrateUpdateStatus
import dev.cl0ud9.krate.platform.selfupdate.SelfUpdateState
import dev.cl0ud9.krate.ui.settings.PLAY_PROTECT_HEADS_UP
import dev.cl0ud9.krate.ui.settings.SelfUpdateAction
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.formatMarkdownLite
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLine

private val NOTES_MAX_HEIGHT = 150.dp

// a proactive "a new version is out" prompt on launch; Update now downloads it and hands it to Android's installer
@Composable
fun KrateUpdateAnnouncementDialog(
    status: KrateUpdateStatus.UpdateAvailable,
    selfUpdateState: SelfUpdateState?,
    onUpdate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val notes = releaseNoteItems(status.releaseNotes.orEmpty())
    KrateDialog(
        header =
            KrateDialogHeader(
                tag = "Krate ${status.latestVersion}",
                icon = painterResource(R.drawable.ic_krate),
                title = rememberKrateLine(Moment.KRATE_UPDATE_AVAILABLE),
                body =
                    "Version ${status.latestVersion} is ready. Updating takes a few seconds " +
                        "and keeps your apps and settings.",
            ),
        onDismissRequest = onDismiss,
        detail =
            if (notes.isEmpty()) {
                null
            } else {
                {
                    ReleaseNotesHint(
                        title = "What's new in ${status.latestVersion}",
                        notes = notes,
                        intro = releaseNoteIntro(status.releaseNotes.orEmpty()),
                    )
                }
            },
        footer = { UpdateFooter(status, selfUpdateState, onUpdate, onDismiss) },
    )
}

@Composable
internal fun ReleaseNotesHint(
    title: String,
    notes: List<String>,
    intro: String? = null,
) {
    KrateDialogHint(icon = painterResource(R.drawable.ic_newspaper_rounded), title = title) {
        Column(
            modifier = Modifier.heightIn(max = NOTES_MAX_HEIGHT).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            intro?.let {
                Text(
                    text = it.formatMarkdownLite(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            notes.forEach { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = note.formatMarkdownLite(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.UpdateFooter(
    status: KrateUpdateStatus.UpdateAvailable,
    selfUpdateState: SelfUpdateState?,
    onUpdate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val downloadUrl = status.downloadUrl
    if (selfUpdateState == null) {
        val uriHandler = LocalUriHandler.current
        if (downloadUrl != null) HelperText(PLAY_PROTECT_HEADS_UP, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onDismiss) { Text("Later") }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { if (downloadUrl != null) onUpdate(downloadUrl) else uriHandler.openUri(status.releaseUrl) },
            shape = ShapeCache.rounded16,
        ) {
            Icon(
                painterResource(R.drawable.ic_arrow_forward_rounded),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text("Update now")
        }
    } else {
        // downloading, installing or a retry after a failure - the same states Settings shows
        // a stopped or finished install isn't busy any more, so Later comes back
        val stopped = (selfUpdateState as? SelfUpdateState.Installing)?.installStatus is InstallStatus.Failed
        val busy =
            !stopped &&
                (selfUpdateState is SelfUpdateState.Downloading || selfUpdateState is SelfUpdateState.Installing)
        Column(modifier = Modifier.fillMaxWidth()) {
            SelfUpdateAction(status = status, selfUpdateState = selfUpdateState, onInstallUpdate = onUpdate)
            if (!busy) TextButton(onClick = onDismiss) { Text("Later") }
        }
    }
}
