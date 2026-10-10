package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.domain.repository.Baseline
import dev.cl0ud9.krate.ui.components.SectionHeader
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.tappableRow
import kotlinx.coroutines.launch
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
        modifier = modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = "Version history", icon = rememberVectorPainter(Icons.Filled.History))
            // where each row sits, so the highlight under the picked one can glide to the next
            val rowSpans = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
            val selectedIndex = artifacts.indexOf(selectedArtifact)
            val haptics = LocalHapticFeedback.current
            Column(
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .selectionHighlight(rowSpans[selectedIndex], MaterialTheme.colorScheme.tertiaryContainer),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                artifacts.forEachIndexed { index, artifact ->
                    VersionRow(
                        modifier =
                            Modifier.onPlaced { placed ->
                                rowSpans[index] = placed.positionInParent().y to placed.size.height.toFloat()
                            },
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
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelectVersion(artifact)
                        },
                    )
                }
            }
            VersionFootnotes(olderThanInstalledListed = olderThanInstalledListed, onlyOne = artifacts.size == 1)
        }
    }
}

// what's worth knowing under the list: going back erases data, or there's only the one version so far
@Composable
private fun VersionFootnotes(
    olderThanInstalledListed: Boolean,
    onlyOne: Boolean,
) {
    // said once, here, rather than appearing above the list when an older build is picked and shoving it down
    if (olderThanInstalledListed) {
        Text(
            text = "Going back to a version older than yours uninstalls the app first, which erases its data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    if (onlyOne) {
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

@Composable
private fun VersionRow(
    artifact: ArtifactInfo,
    tags: List<String>,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .tappableRow(enabled = !isSelected && !artifact.withdrawn, pressFeedback = false, onClick = onClick),
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
    if (withdrawn && !isSelected) return
    // the tick pops in on a spring as "Use this version" fades out, and the other way round on the row let go of
    AnimatedContent(
        targetState = isSelected,
        transitionSpec = {
            if (targetState) {
                // the old label is gone before the tick arrives, so the two never overlap mid-swap
                (
                    fadeIn(tween(SELECT_FADE_MS, delayMillis = SELECT_FADE_MS / 2)) +
                        scaleIn(spring(SELECT_DAMPING, SELECT_STIFFNESS), SELECT_FROM)
                ).togetherWith(fadeOut(tween(SELECT_FADE_MS / 2)))
            } else {
                fadeIn(tween(SELECT_FADE_MS, delayMillis = SELECT_FADE_MS / 2))
                    .togetherWith(fadeOut(tween(SELECT_FADE_MS / 2)) + scaleOut(targetScale = SELECT_FROM))
            }.using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterEnd,
        label = "version-selected",
    ) { selected ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (selected) {
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
            } else {
                Text(
                    text = "Use this version",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// a soft fill under the picked row that moves to the next one picked like a drop of liquid: the edge facing the new row
// sets off at once, the other follows a beat later, so the fill stretches across and then gathers on its row. It starts
// where it belongs, so only a real change of pick moves it
@Composable
private fun Modifier.selectionHighlight(
    span: Pair<Float, Float>?,
    color: Color,
): Modifier {
    val top = remember { Animatable(0f) }
    val bottom = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(span) {
        val (to, tall) = span ?: return@LaunchedEffect
        if (!placed) {
            top.snapTo(to)
            bottom.snapTo(to + tall)
            placed = true
        } else {
            val goingDown = to > top.value
            val lead = spring<Float>(LEAD_DAMPING, LEAD_STIFFNESS)
            val trail = spring<Float>(TRAIL_DAMPING, TRAIL_STIFFNESS)
            launch { top.animateTo(to, if (goingDown) trail else lead) }
            bottom.animateTo(to + tall, if (goingDown) lead else trail)
        }
    }
    val fill = color.copy(alpha = HIGHLIGHT_ALPHA)
    return drawBehind {
        if (!placed || span == null) return@drawBehind
        val bleed = HIGHLIGHT_BLEED.toPx()
        drawRoundRect(
            color = fill,
            topLeft = Offset(-bleed, top.value),
            size = Size(size.width + bleed * 2, (bottom.value - top.value).coerceAtLeast(0f)),
            cornerRadius = CornerRadius(HIGHLIGHT_CORNER.toPx()),
        )
    }
}

private val HIGHLIGHT_BLEED = 8.dp
private val HIGHLIGHT_CORNER = 12.dp
private const val HIGHLIGHT_ALPHA = 0.45f

// the leading edge is quick and lands firm; the trailing one is softer, with a touch of give as it arrives
private const val LEAD_DAMPING = 0.9f
private const val LEAD_STIFFNESS = 700f
private const val TRAIL_DAMPING = 0.72f
private const val TRAIL_STIFFNESS = 260f
private const val SELECT_FADE_MS = 180
private const val SELECT_DAMPING = 0.55f
private const val SELECT_STIFFNESS = 500f
private const val SELECT_FROM = 0.6f

// with several themes side by side, each theme's newest build is its own latest
private fun ArtifactInfo.isNewestOfItsTheme(artifacts: List<ArtifactInfo>): Boolean =
    label != null && artifacts.firstOrNull { it.label == label && !it.withdrawn } == this
