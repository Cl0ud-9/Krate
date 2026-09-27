package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
private val CARD_PADDING = 16.dp
private const val MISSING = "No release notes for this version."
private const val MISSING_WITH_LINK = "No notes here for this version."
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

// release notes for the selected build, or for everything since the installed one; a few lines until expanded.
// The card fits its notes; whether they need "Show more" is worked out before the first draw, so the button never
// pops in a frame late and nudges what's below
@Composable
internal fun ReleaseNotesSection(
    app: AppProfile,
    builds: List<ArtifactInfo>,
    onCollapse: () -> Unit = {},
) {
    // a different pick starts from the preview again
    var expanded by remember(app.id, builds.firstOrNull()) { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val truncated =
            rememberPreviewTruncated(
                builds.firstOrNull()?.let { notesFor(app, builds, it) },
                maxWidth - CARD_PADDING * 2,
            )
        NotesCard(app, builds, expanded, truncated, onToggle = {
            if (expanded) onCollapse()
            expanded = !expanded
        })
    }
}

@Composable
private fun NotesCard(
    app: AppProfile,
    builds: List<ArtifactInfo>,
    expanded: Boolean,
    truncated: Boolean,
    onToggle: () -> Unit,
) {
    val shown = if (expanded) builds else builds.take(1)
    val missingPage = shown.firstOrNull()?.takeIf { notesFor(app, builds, it) == null }?.releasePageUrl()
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = if (builds.size > 1) "What's new since yours" else "Release notes",
                icon = rememberVectorPainter(Icons.Filled.Description),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (shown.isEmpty()) {
                NotesBody(text = "No release notes available.", collapsed = !expanded)
            }
            shown.forEachIndexed { index, build ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
                }
                BuildNotes(
                    build = build,
                    notes = notesFor(app, builds, build),
                    collapsed = !expanded,
                )
            }
            NotesActionSlot(
                label =
                    when {
                        expanded -> "Show less"
                        builds.size > 1 -> "Show all ${builds.size} versions"
                        truncated -> "Show more"
                        else -> null
                    },
                releasePage = missingPage,
                onToggle = onToggle,
            )
        }
    }
}

private fun notesFor(
    app: AppProfile,
    builds: List<ArtifactInfo>,
    build: ArtifactInfo,
): String? = build.releaseNotes ?: app.releaseNotes.takeIf { build == builds.first() }

// the card's bottom-right action: the expand toggle, or a way to the notes on GitHub; nothing when neither applies
@Composable
private fun NotesActionSlot(
    label: String?,
    releasePage: String?,
    onToggle: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    if (label == null && releasePage == null) return
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        if (label != null) {
            TextButton(onClick = onToggle) { Text(label) }
        } else if (releasePage != null) {
            TextButton(onClick = { uriHandler.openUri(releasePage) }) { Text("See the release on GitHub") }
        }
    }
}

// whether the collapsed preview of these notes runs past its lines, measured up front at the width the card gives them
@Composable
private fun rememberPreviewTruncated(
    notes: String?,
    availableWidth: Dp,
): Boolean {
    if (notes == null) return false
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium
    val (preview, skippedIntro) = remember(notes) { releaseNotesPreview(notes) }
    val formatted = preview.formatMarkdownLite()
    val width = with(LocalDensity.current) { availableWidth.roundToPx() }
    return remember(formatted, style, width) {
        skippedIntro ||
            measurer
                .measure(formatted, style, maxLines = COLLAPSED_LINES, constraints = Constraints(maxWidth = width))
                .hasVisualOverflow
    }
}

// one build's version and date, then its notes
@Composable
private fun BuildNotes(
    build: ArtifactInfo,
    notes: String?,
    collapsed: Boolean,
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
        NotesBody(
            text = notes ?: if (build.releasePageUrl() != null) MISSING_WITH_LINK else MISSING,
            collapsed = collapsed,
            muted = notes == null,
        )
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

// a few lines of the changes while collapsed, all of it once expanded
@Composable
private fun NotesBody(
    text: String,
    collapsed: Boolean,
    muted: Boolean = false,
) {
    // collapsed, the preview opens on the changes, past any intro
    val shown = remember(text, collapsed) { if (collapsed) releaseNotesPreview(text).first else text }
    Text(
        text = shown.formatMarkdownLite(),
        style = MaterialTheme.typography.bodyMedium,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        maxLines = if (collapsed) COLLAPSED_LINES else Int.MAX_VALUE,
    )
}
