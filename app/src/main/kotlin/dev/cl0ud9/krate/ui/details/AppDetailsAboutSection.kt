package dev.cl0ud9.krate.ui.details

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
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
import dev.cl0ud9.krate.domain.model.SetupKind
import dev.cl0ud9.krate.domain.model.SetupStep
import dev.cl0ud9.krate.domain.model.isTracked
import dev.cl0ud9.krate.domain.model.trackedRef
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.ui.components.KrateDialog
import dev.cl0ud9.krate.ui.components.KrateDialogHeader
import dev.cl0ud9.krate.ui.components.KrateSwitch
import dev.cl0ud9.krate.ui.components.SectionHeader
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache

private const val DIVIDER_ALPHA = 0.4f

// the label on the box: what the app does, where it's packed from, how updates arrive, and what it needs alongside
@Composable
internal fun AppInfoSection(
    app: AppProfile,
    dependencies: List<DependencyInfo>,
    onNavigateToApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = sourceOf(app)
    Card(
        modifier = modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = "What's in the Krate",
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
                PackedFromRow(source, tracked = app.isTracked)
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
                    Column {
                        dependencies.forEach { dependency ->
                            DependencyRow(dependency = dependency, onClick = { onNavigateToApp(dependency.app.id) })
                        }
                    }
                }
            }
            val steps =
                app.guide
                    ?.setup
                    ?.filter { it.kind != SetupKind.IN_APP }
                    .orEmpty()
            if (steps.isNotEmpty()) {
                Divider()
                InfoRow(icon = painterResource(R.drawable.ic_security_rounded), title = "What it asks for") {
                    AsksFor(steps)
                }
            }
            if (app.isTracked) {
                Divider()
                TestVersionsRow(app)
                Divider()
                StopTrackingRow(app)
            }
        }
    }
}

// a tracked app's test releases: off unless asked for, since they can be rough
@Composable
private fun TestVersionsRow(app: AppProfile) {
    val context = LocalContext.current
    // shown at once; the saved choice catches up as the app's list is read again
    var on by rememberSaveable(app.trackedPrerelease) { mutableStateOf(app.trackedPrerelease) }
    InfoRow(icon = painterResource(R.drawable.ic_update_rounded), title = "Test versions") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Also offer releases the developer marks as not ready yet. They can be less stable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            KrateSwitch(
                checked = on,
                onCheckedChange = { include ->
                    on = include
                    context.appContainer().trackedAppsRepository.setPrerelease(app.id, include)
                },
            )
        }
    }
}

// the last word on an app followed from its repository: letting it go, said plainly (it stays on the phone, Krate just
// stops checking its releases)
@Composable
private fun StopTrackingRow(app: AppProfile) {
    val context = LocalContext.current
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var asking by rememberSaveable { mutableStateOf(false) }
    val ref = app.trackedRef
    InfoRow(icon = painterResource(siteIcon(ref?.forge)), title = "Tracked by you") {
        BodyText(
            "Krate checks ${ref?.slug} on ${ref?.forge?.siteName} for new releases. Stop any time, and the app stays " +
                "on your phone.",
        )
        TextButton(
            onClick = { asking = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
            modifier = Modifier.offset(x = (-12).dp),
        ) { Text("Stop tracking") }
    }
    if (asking) {
        KrateDialog(
            header =
                KrateDialogHeader(
                    tag = "Stop tracking",
                    icon = painterResource(siteIcon(ref?.forge)),
                    title = "Stop tracking ${app.displayName}?",
                    body =
                        "${app.displayName} stays on your phone with its data. Krate just stops checking its " +
                            "releases and takes it off your list. You can track it again any time.",
                ),
            onDismissRequest = { asking = false },
            footer = {
                TextButton(onClick = { asking = false }) { Text("Keep tracking") }
                Button(onClick = {
                    asking = false
                    val container = context.appContainer()
                    container.artifactDownloader.forgetDownloads(app)
                    container.downloadProgressNotifier.dismiss(app.id)
                    container.trackedAppsRepository.forget(app.id)
                    back?.onBackPressed()
                }, shape = ShapeCache.rounded16) { Text("Stop tracking") }
            },
        )
    }
}

// the permissions it needs, said before installing, so nothing it asks for afterwards comes as a surprise
@Composable
private fun AsksFor(steps: List<SetupStep>) {
    WhatItDoes(highlights = steps.map { if (it.optional) "${it.title} (optional)" else it.title })
    BodyText("Once it's installed, Krate takes you to each one in a tap.")
}

// the source repo as a tappable link, or a word on Krate's own builds; either way each download is checked on arrival
@Composable
private fun PackedFromRow(
    source: PackedFrom,
    tracked: Boolean,
) {
    val link = MaterialTheme.colorScheme.primary
    val text =
        buildAnnotatedString {
            when (source) {
                is PackedFrom.Repo -> {
                    append("Straight from ")
                    withLink(
                        LinkAnnotation.Url(
                            "https://${source.forge.host}/${source.slug}",
                            TextLinkStyles(style = SpanStyle(color = link)),
                        ),
                    ) { append(source.slug) }
                    append(" on ${source.forge.siteName}, with no middlemen. ")
                }
                PackedFrom.KrateBuilds -> append("Built and signed by Krate itself. ")
            }
            append(
                if (tracked) {
                    "You added it yourself, and Krate only installs builds signed by the same developer as " +
                        "that first one."
                } else {
                    "Every download's fingerprint is checked before it's installed."
                },
            )
        }
    val icon = if (source is PackedFrom.Repo) siteIcon(source.forge) else R.drawable.ic_krate
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
                    .clip(ShapeCache.rounded12)
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
