package dev.cl0ud9.krate.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.ui.components.Reveal
import dev.cl0ud9.krate.ui.components.swapTransition
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.navigation.pressable

private const val BADGE_BLEND_MS = 300
private val ROW_MIN_HEIGHT = 88.dp
private val BADGE_SIZE = 56.dp
private val BADGE_SIZE_BIG_TEXT = 40.dp
private const val BIG_TEXT_SCALE = 1.6f
private val BADGE_ICON_SIZE = 24.dp
private val GROUP_OUTER_RADIUS = 24.dp
private val GROUP_INNER_RADIUS = 4.dp

// bundles the badge's container/content color pair - keeps SettingsRow under detekt's
// parameter-count threshold without collapsing them into a single ambiguous "tint" value
internal data class SettingsRowColors(
    val badgeColor: Color,
    val contentColor: Color,
)

@Composable
internal fun defaultSettingsRowColors() =
    SettingsRowColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)

// the icon/title/subtitle/colors that make up a row's header, bundled together so SettingsRow
// itself stays under detekt's parameter-count threshold
internal data class SettingsRowHeader(
    val icon: Painter,
    val title: String,
    val subtitle: String,
    val colors: SettingsRowColors,
)

// one continuous grouped list instead of separate floating cards: adjacent rows sit 2dp apart with
// their touching corners nearly square and their outer corners fully rounded, so the whole group
// reads as one settings block. A round icon badge (not a squircle) per row, sized to a fixed
// 88dp-minimum row height - taller rows (an expanded section below) just grow past that minimum
@Suppress("LongParameterList")
@Composable
internal fun SettingsRow(
    header: SettingsRowHeader,
    shape: Shape,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    expandedContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = ROW_MIN_HEIGHT)
    val content: @Composable () -> Unit = {
        // no size animation here: every part that changes height (a line, a note) eases on the one shared curve, so the
        // row grows in a single motion rather than in steps
        // at the biggest text sizes the badge and gaps give the words the room instead of squeezing them
        val bigText = LocalDensity.current.fontScale >= BIG_TEXT_SCALE
        Column(modifier = Modifier.padding(if (bigText) 12.dp else 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (bigText) 10.dp else 16.dp),
            ) {
                RowBadge(header)
                RowTexts(header, Modifier.weight(1f))
                trailing?.invoke()
            }
            RowNote(expandedContent)
        }
    }
    // a separate onClick overload of Surface, not `enabled = onClick != null` on the clickable one -
    // that overload dims its content whenever disabled, which would incorrectly fade every row that
    // simply isn't meant to be tappable at all (Automatic Downloads, Storage, About)
    if (onClick != null) {
        Surface(
            modifier = rowModifier.glassRim(shape).pressable(shape, onClick),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = rowModifier.glassRim(shape),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            content()
        }
    }
}

// the round badge: when a row's look changes (its colour, its icon) the badge blends to the new one
@Composable
private fun RowBadge(header: SettingsRowHeader) {
    val badge by animateColorAsState(header.colors.badgeColor, tween(BADGE_BLEND_MS), label = "row-badge")
    val glyph by animateColorAsState(header.colors.contentColor, tween(BADGE_BLEND_MS), label = "row-glyph")
    Box(
        modifier =
            Modifier
                .size(
                    if (LocalDensity.current.fontScale >=
                        BIG_TEXT_SCALE
                    ) {
                        BADGE_SIZE_BIG_TEXT
                    } else {
                        BADGE_SIZE
                    },
                ).background(badge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = header.icon, animationSpec = tween(BADGE_BLEND_MS), label = "row-icon") { icon ->
            Icon(icon, contentDescription = null, tint = glyph, modifier = Modifier.size(BADGE_ICON_SIZE))
        }
    }
}

// the title and its line; a changed line fades through and the row eases to its new height, rather than snapping
@Composable
private fun RowTexts(
    header: SettingsRowHeader,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AnimatedContent(
            targetState = header.title,
            transitionSpec = swapTransition(),
            label = "row-title",
        ) { title ->
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        AnimatedContent(
            targetState = header.subtitle,
            transitionSpec = swapTransition(),
            label = "row-subtitle",
        ) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// drops in under the row and folds away again, keeping the last note it had while it folds
@Composable
private fun RowNote(content: (@Composable ColumnScope.() -> Unit)?) {
    var last by remember { mutableStateOf(content) }
    if (content != null) last = content
    Reveal(visible = content != null, gap = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { last?.invoke(this) }
        }
    }
}

// the first row in a group is square-round outside/nearly-square inside, the last is the mirror,
// everything in between is nearly square on all sides, and a lone row is fully rounded all around.
// Remembered per corner set, so a recomposition reuses the same shape
@Composable
internal fun settingsGroupShape(
    index: Int,
    total: Int,
): Shape =
    when {
        total <= 1 -> groupCorners(GROUP_OUTER_RADIUS, GROUP_OUTER_RADIUS, GROUP_OUTER_RADIUS, GROUP_OUTER_RADIUS)
        index == 0 -> groupCorners(GROUP_OUTER_RADIUS, GROUP_OUTER_RADIUS, GROUP_INNER_RADIUS, GROUP_INNER_RADIUS)
        index == total - 1 ->
            groupCorners(
                GROUP_INNER_RADIUS,
                GROUP_INNER_RADIUS,
                GROUP_OUTER_RADIUS,
                GROUP_OUTER_RADIUS,
            )
        else -> groupCorners(GROUP_INNER_RADIUS, GROUP_INNER_RADIUS, GROUP_INNER_RADIUS, GROUP_INNER_RADIUS)
    }

@Composable
private fun groupCorners(
    topStart: Dp,
    topEnd: Dp,
    bottomStart: Dp,
    bottomEnd: Dp,
): Shape =
    remember(topStart, topEnd, bottomStart, bottomEnd) {
        RoundedCornerShape(topStart = topStart, topEnd = topEnd, bottomStart = bottomStart, bottomEnd = bottomEnd)
    }
