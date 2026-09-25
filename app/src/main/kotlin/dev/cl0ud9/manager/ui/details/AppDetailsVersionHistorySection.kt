package dev.cl0ud9.manager.ui.details

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.ArtifactInfo
import dev.cl0ud9.manager.domain.model.latestArtifact
import dev.cl0ud9.manager.domain.repository.Baseline
import dev.cl0ud9.manager.ui.components.SectionHeader
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.pressScale
import java.text.DateFormat
import java.util.Date

// only renders when more than one build is currently retained - a single-artifact app has nothing
// to pick between. The point is recovery: if the newest build misbehaves, any of the previous
// ones can be installed from here (a withdrawn build is listed but can't be picked). For a patched
// app each row is a patches release, each on the newest app version it supported
@Composable
internal fun VersionHistorySection(
    app: AppProfile,
    installedBuild: Baseline?,
    selectedArtifact: ArtifactInfo?,
    onSelectVersion: (ArtifactInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artifacts = app.artifacts
    val latestArtifact = app.latestArtifact
    if (artifacts.size <= 1) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = "Version history", icon = rememberVectorPainter(Icons.Filled.History))
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                artifacts.forEach { artifact ->
                    VersionRow(
                        artifact = artifact,
                        tags =
                            listOfNotNull(
                                "Latest".takeIf { artifact == latestArtifact },
                                "Installed".takeIf { installedBuild != null && artifact.matches(installedBuild) },
                            ),
                        isSelected = artifact == selectedArtifact,
                        onClick = { onSelectVersion(artifact) },
                    )
                }
            }
        }
    }
}

// a baseline recorded before builds had ids only knows its version, which is enough for apps that
// publish one build per version
private fun ArtifactInfo.matches(baseline: Baseline): Boolean =
    if (baseline.buildId != null) buildId == baseline.buildId else versionName == baseline.versionName

@Composable
private fun VersionRow(
    artifact: ArtifactInfo,
    tags: List<String>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .clickable(
                    enabled = !isSelected && !artifact.withdrawn,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val patches = artifact.patchesVersionName
            Text(
                text = if (patches != null) "Patches $patches" else artifact.versionName,
                style = MaterialTheme.typography.bodyMedium,
            )
            val details =
                listOfNotNull(
                    patches?.let { "Version ${artifact.versionName}" },
                    artifact.label,
                    artifact.publishedAtMillis?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) },
                ) + tags
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val note = if (artifact.withdrawn) artifact.withdrawnReason ?: "Withdrawn" else artifact.note
            if (note != null) {
                Text(
                    text = if (artifact.withdrawn && artifact.withdrawnReason != null) "Withdrawn: $note" else note,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (artifact.withdrawn) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
        VersionRowTrailing(isSelected = isSelected, withdrawn = artifact.withdrawn)
    }
}

@Composable
private fun VersionRowTrailing(
    isSelected: Boolean,
    withdrawn: Boolean,
) {
    when {
        isSelected -> {
            Icon(
                painterResource(R.drawable.ic_check_circle_rounded),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        !withdrawn ->
            Text(
                text = "Use this version",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
    }
}
