package dev.cl0ud9.krate.ui.details

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.domain.repository.Baseline
import dev.cl0ud9.krate.ui.components.SectionHeader
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.tappableRow
import java.text.DateFormat
import java.util.Date

// every retained build, so a misbehaving newest one can be swapped for an earlier one (a withdrawn build is
// listed but can't be picked); with only one build it says so rather than vanishing. For an app Krate builds itself
// each row is one of its builds
@Suppress("LongParameterList")
@Composable
internal fun VersionHistorySection(
    app: AppProfile,
    installedBuild: Baseline?,
    selectedArtifact: ArtifactInfo?,
    // some listed build is older than the one on the device, so picking it means uninstalling first
    olderThanInstalledListed: Boolean,
    onSelectVersion: (ArtifactInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artifacts = app.artifacts
    val latestArtifact = app.latestArtifact
    if (artifacts.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = "Version history", icon = rememberVectorPainter(Icons.Filled.History))
            Column(modifier = Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                artifacts.forEach { artifact ->
                    VersionRow(
                        artifact = artifact,
                        tags =
                            listOfNotNull(
                                "Latest".takeIf {
                                    artifact == latestArtifact ||
                                        artifact.isNewestOfItsTheme(
                                            artifacts,
                                        )
                                },
                                "Installed".takeIf { installedBuild != null && artifact.isBuildOf(installedBuild) },
                            ),
                        isSelected = artifact == selectedArtifact,
                        onClick = { onSelectVersion(artifact) },
                    )
                }
            }
            // said once, here, rather than appearing above the list when an older build is picked and shoving it down
            if (olderThanInstalledListed) {
                Text(
                    text = "Going back to a version older than yours uninstalls the app first, which erases its data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (artifacts.size == 1) {
                Text(
                    text =
                        "This is the only version available right now. When a newer one arrives, this one stays " +
                            "here so you can come back to it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun VersionRow(
    artifact: ArtifactInfo,
    tags: List<String>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().tappableRow(enabled = !isSelected && !artifact.withdrawn, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val patches = artifact.patchesVersionName
            Text(
                text = if (patches != null) "Build $patches" else artifact.versionName,
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

// with several themes side by side, each theme's newest build is its own latest
private fun ArtifactInfo.isNewestOfItsTheme(artifacts: List<ArtifactInfo>): Boolean =
    label != null && artifacts.firstOrNull { it.label == label && !it.withdrawn } == this
