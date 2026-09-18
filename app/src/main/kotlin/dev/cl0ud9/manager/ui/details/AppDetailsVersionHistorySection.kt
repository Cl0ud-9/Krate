package dev.cl0ud9.manager.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.domain.model.ArtifactInfo
import dev.cl0ud9.manager.ui.components.SectionHeader
import dev.cl0ud9.manager.ui.theme.ShapeCache

// only renders when more than one version is currently retained - a single-artifact app (the
// common case) has nothing to pick between, so this section simply doesn't exist for it rather
// than showing a list of one
@Composable
internal fun VersionHistorySection(
    artifacts: List<ArtifactInfo>,
    selectedArtifact: ArtifactInfo?,
    onSelectVersion: (ArtifactInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artifacts.size <= 1) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = "Version history", icon = Icons.Filled.History)
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                artifacts.forEach { artifact ->
                    VersionRow(
                        artifact = artifact,
                        isSelected = artifact == selectedArtifact,
                        onClick = { onSelectVersion(artifact) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionRow(
    artifact: ArtifactInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isSelected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = artifact.versionName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        } else {
            Text(
                text = "Use this version",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
