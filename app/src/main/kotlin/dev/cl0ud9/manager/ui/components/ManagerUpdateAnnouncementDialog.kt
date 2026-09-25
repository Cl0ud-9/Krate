package dev.cl0ud9.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import dev.cl0ud9.manager.platform.selfupdate.SelfUpdateState
import dev.cl0ud9.manager.ui.settings.SelfUpdateAction
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.formatMarkdownLite

private val NOTES_MAX_HEIGHT = 180.dp

// a proactive "a new version is out" prompt on launch instead of a check tucked away in Settings.
// Updates in place: Update now downloads the release and hands it to Android's installer, the same
// flow Settings uses - no trip to GitHub. Shown at most once per app session (see HomeViewModel)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerUpdateAnnouncementDialog(
    status: ManagerUpdateStatus.UpdateAvailable,
    selfUpdateState: SelfUpdateState?,
    onUpdate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = ShapeCache.smooth28,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.20f),
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                ),
                        ).padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnnouncementHeader()
                Text(
                    text = "Version ${status.latestVersion} is ready",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                val notes = status.releaseNotes?.takeIf { it.isNotBlank() }
                if (notes != null) {
                    Text(
                        text = notes.formatMarkdownLite(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.heightIn(max = NOTES_MAX_HEIGHT).verticalScroll(rememberScrollState()),
                    )
                } else {
                    Text(
                        text = "A newer version of App Manager is available.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // primary action first, the quiet dismiss under it
                SelfUpdateAction(status = status, selfUpdateState = selfUpdateState, onInstallUpdate = onUpdate)
                val busy =
                    selfUpdateState is SelfUpdateState.Downloading || selfUpdateState is SelfUpdateState.Installing
                if (!busy) {
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Later")
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementHeader() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                painter = painterResource(R.drawable.ic_system_update_alt_rounded),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(10.dp).size(26.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "App Manager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "Update available",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
