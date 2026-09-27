package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.repository.Baseline
import dev.cl0ud9.krate.domain.repository.isNewerThan
import dev.cl0ud9.krate.ui.components.SectionHeader
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.formatMarkdownLite
import dev.cl0ud9.krate.ui.util.releaseNotesPreview
import java.text.DateFormat
import java.util.Date

private const val COLLAPSED_LINES = 6
private const val DIVIDER_ALPHA = 0.4f
private val RELEASE_ASSET_URL = Regex("https://github\\.com/([^/]+)/([^/]+)/releases/download/([^/]+)/[^/]+")

// every build between the installed one and the selected one when that's an update, otherwise just the selected one
internal fun releaseNotesBuilds(
    app: AppProfile,
    selected: ArtifactInfo?,
    installed: Baseline?,
): List<ArtifactInfo> {
    val chosen = selected ?: return emptyList()
    val newer =
        installed?.takeIf { chosen.isNewerThan(it, app.artifacts) }?.let { baseline ->
            app.artifacts
                .dropWhile { it != chosen }
                .takeWhile { it.isNewerThan(baseline, app.artifacts) }
                .filter { it == chosen || !it.withdrawn }
        }
    return newer?.ifEmpty { null } ?: listOf(chosen)
}

// release notes for the selected build, or for everything since the installed one; a few lines until expanded
@Composable
internal fun ReleaseNotesSection(
    app: AppProfile,
    builds: List<ArtifactInfo>,
    onCollapse: () -> Unit = {},
) {
    var expanded by remember(app.id) { mutableStateOf(false) }
    var truncated by remember(app.id, builds) { mutableStateOf(false) }
    val shown = if (expanded) builds else builds.take(1)
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = if (builds.size > 1) "What's new since yours" else "Release notes",
                icon = rememberVectorPainter(Icons.Filled.Description),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (shown.isEmpty()) {
                NotesBody(text = "No release notes available.", maxLines = Int.MAX_VALUE) {}
            }
            shown.forEachIndexed { index, build ->
                if (index >
                    0
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
                }
                BuildNotes(
                    build = build,
                    notes = build.releaseNotes ?: app.releaseNotes.takeIf { build == builds.first() },
                    maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                    onTruncated = { truncated = it || truncated },
                )
            }
            if (expanded || truncated || builds.size > 1) {
                TextButton(
                    onClick = {
                        if (expanded) onCollapse()
                        expanded = !expanded
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        if (expanded) {
                            "Show less"
                        } else if (builds.size >
                            1
                        ) {
                            "Show all ${builds.size} versions"
                        } else {
                            "Show more"
                        },
                    )
                }
            }
        }
    }
}

// one build's version and date, then its notes
@Composable
private fun BuildNotes(
    build: ArtifactInfo,
    notes: String?,
    maxLines: Int,
    onTruncated: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = ShapeCache.smoothPill, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = build.patchesVersionName?.let { "${build.versionName}, patches $it" } ?: build.versionName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            build.publishedAtMillis?.let {
                Text(
                    text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (notes != null) {
            NotesBody(text = notes, maxLines = maxLines, onTruncated = onTruncated)
        } else {
            MissingNotes(releasePage = build.releasePageUrl())
        }
    }
}

// nothing to show here, but a public build's own release page on GitHub always has the story
@Composable
private fun MissingNotes(releasePage: String?) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = if (releasePage != null) "No notes here for this version." else "No release notes for this version.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (releasePage != null) {
        TextButton(onClick = { uriHandler.openUri(releasePage) }, contentPadding = PaddingValues(0.dp)) {
            Text("See the release on GitHub")
        }
    }
}

// a public GitHub release asset's own release page; null for anything else, including private builds
internal fun ArtifactInfo.releasePageUrl(): String? =
    if (requiresAuth) {
        null
    } else {
        RELEASE_ASSET_URL.matchEntire(downloadUrl)?.destructured?.let { (owner, repo, tag) ->
            "https://github.com/$owner/$repo/releases/tag/$tag"
        }
    }

@Composable
private fun NotesBody(
    text: String,
    maxLines: Int,
    onTruncated: (Boolean) -> Unit,
) {
    // collapsed, the preview opens on the changes; a skipped intro still counts as more to show
    val collapsed = maxLines != Int.MAX_VALUE
    val (shown, skippedIntro) =
        remember(
            text,
            collapsed,
        ) { if (collapsed) releaseNotesPreview(text) else text to false }
    Text(
        text = shown.formatMarkdownLite(),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = maxLines,
        onTextLayout = { onTruncated(it.hasVisualOverflow || skippedIntro) },
    )
}
