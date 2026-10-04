package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.ui.components.StatusRow

// an older build, or another build of the same version, picked in version history. The card is about that pick
// now, so Open makes way for a way back
@Composable
internal fun RollbackContent(
    state: AppDetailsUiState,
    uninstalling: Boolean,
    onDownload: () -> Unit,
    onBackToLatest: () -> Unit,
) {
    StatusRow(
        icon = rememberVectorPainter(Icons.Filled.History),
        tint = MaterialTheme.colorScheme.primary,
        text =
            if (state.isSwitch) {
                "Other build selected: ${state.selectedArtifact?.buildDescription()}."
            } else {
                "Older version selected: ${state.selectedArtifact?.buildDescription()}."
            },
    )
    // no uninstall warning here: picking a version must not change this section's height and shove the list below;
    // the version list says it once, and the install step says it again right before it happens
    if (uninstalling) {
        UninstallingStatus(installStatus = state.installStatus)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onDownload, enabled = state.selectedArtifact != null, modifier = Modifier.fillMaxWidth()) {
                Text(pickedVersionLabel(state))
            }
            Button(
                onClick = onBackToLatest,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ) {
                Text("Back to latest")
            }
        }
    }
}

// names the picked version, so it can't be mistaken for the installed one
private fun pickedVersionLabel(state: AppDetailsUiState): String {
    val label = downloadLabelFor(state)
    val version = state.selectedArtifact?.versionName
    return when {
        label != "Download" || version == null -> label
        state.isSwitch -> "Download this build"
        else -> "Download $version"
    }
}
