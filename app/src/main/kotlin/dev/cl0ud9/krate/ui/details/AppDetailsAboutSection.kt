package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.ui.components.SectionHeader
import dev.cl0ud9.krate.ui.theme.ShapeCache

private const val DIVIDER_ALPHA = 0.4f
private val GITHUB_RELEASE_URL = Regex("""^https://github\.com/([^/]+)/([^/]+)/releases/""")

// where an app's builds come from, read off its download link: a public GitHub repo, or Krate's own patched builds
internal sealed interface PackedFrom {
    data class Repo(
        val slug: String,
    ) : PackedFrom

    data object KrateBuilds : PackedFrom
}

// builds behind a token are the patched ones Krate builds and signs itself; otherwise the repo in the link
internal fun packedFrom(
    downloadUrl: String?,
    requiresAuth: Boolean,
): PackedFrom? =
    when {
        requiresAuth -> PackedFrom.KrateBuilds
        downloadUrl == null -> null
        else ->
            GITHUB_RELEASE_URL
                .find(
                    downloadUrl,
                )?.let { PackedFrom.Repo("${it.groupValues[1]}/${it.groupValues[2]}") }
    }

// the label on the box: what the app does, where it's packed from, how updates arrive, and what it needs alongside
@Composable
internal fun AppInfoSection(
    app: AppProfile,
    dependencies: List<DependencyInfo>,
    onNavigateToApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = app.latestArtifact
    val source = packedFrom(latest?.downloadUrl, latest?.requiresAuth == true)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = "What's in the box",
                icon = painterResource(R.drawable.ic_krate),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (app.highlights.isNotEmpty()) {
                InfoRow(icon = painterResource(R.drawable.ic_info_rounded), title = "What it does") {
                    WhatItDoes(highlights = app.highlights)
                }
                Divider()
            }
            if (source != null) {
                PackedFromRow(source)
                Divider()
            }
            InfoRow(icon = rememberVectorPainter(Icons.Filled.Build), title = "How updates land") {
                BodyText(updatesDescription(app.installationMode))
            }
            Divider()
            InfoRow(icon = rememberVectorPainter(Icons.Filled.AccountTree), title = "Needs") {
                if (dependencies.isEmpty()) {
                    BodyText("Nothing else. It travels light.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        dependencies.forEach { dependency ->
                            DependencyRow(dependency = dependency, onClick = { onNavigateToApp(dependency.app.id) })
                        }
                    }
                }
            }
        }
    }
}

// the source repo as a tappable link, or a word on Krate's own builds; either way each download is checked on arrival
@Composable
private fun PackedFromRow(source: PackedFrom) {
    val link = MaterialTheme.colorScheme.primary
    val text =
        buildAnnotatedString {
            when (source) {
                is PackedFrom.Repo -> {
                    append("Straight from ")
                    withLink(
                        LinkAnnotation.Url(
                            "https://github.com/${source.slug}",
                            TextLinkStyles(style = SpanStyle(color = link)),
                        ),
                    ) { append(source.slug) }
                    append(" on GitHub, with no middlemen. ")
                }
                PackedFrom.KrateBuilds -> append("Patched and signed by Krate itself. ")
            }
            append("Every download's fingerprint is checked before it's installed.")
        }
    val icon = if (source is PackedFrom.Repo) R.drawable.ic_github else R.drawable.ic_krate
    InfoRow(icon = painterResource(icon), title = "Where it comes from") {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun updatesDescription(mode: InstallationMode): String =
    when (mode) {
        InstallationMode.UPDATE ->
            "Each new version slides in over the old one, and your data stays put. If one won't go on top, " +
                "you can reinstall the app from scratch instead, which clears its data."
        InstallationMode.CLEAN_INSTALL ->
            "Every update is a fresh unpack: the old version comes out first and takes its data with it."
    }

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
}

@Composable
private fun BodyText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// a leading icon badge, a title and whatever content belongs under it - an M3 list row
@Composable
private fun InfoRow(
    icon: Painter,
    title: String,
    content: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(ShapeCache.smooth12)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

// the app's highlights as a short dotted list
@Composable
private fun WhatItDoes(highlights: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        highlights.forEach { highlight ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = highlight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
