package dev.cl0ud9.manager.ui.settings

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.ui.theme.LocalUseSmoothCorners
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private val ROW_MIN_HEIGHT = 88.dp
private val BADGE_SIZE = 56.dp
private val BADGE_ICON_SIZE = 24.dp
private val GROUP_OUTER_RADIUS = 24.dp
private val GROUP_INNER_RADIUS = 4.dp
private const val GROUP_SMOOTHNESS = 60

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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier.size(BADGE_SIZE).background(header.colors.badgeColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        header.icon,
                        contentDescription = null,
                        tint = header.colors.contentColor,
                        modifier = Modifier.size(BADGE_ICON_SIZE),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = header.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = header.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                trailing?.invoke()
            }
            if (expandedContent != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = expandedContent)
            }
        }
    }
    // a separate onClick overload of Surface, not `enabled = onClick != null` on the clickable one -
    // that overload dims its content whenever disabled, which would incorrectly fade every row that
    // simply isn't meant to be tappable at all (Automatic Downloads, Storage, About)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = rowModifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            content()
        }
    } else {
        Surface(modifier = rowModifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
            content()
        }
    }
}

// the first row in a group is square-round outside/nearly-square inside, the last is the mirror,
// everything in between is nearly square on all sides, and a lone row is fully rounded all around.
// Remembered per corner set and smooth-corners setting, so a recomposition reuses the same shape
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
): Shape {
    val useSmoothCorners = LocalUseSmoothCorners.current
    return remember(topStart, topEnd, bottomStart, bottomEnd, useSmoothCorners) {
        if (useSmoothCorners) {
            AbsoluteSmoothCornerShape(
                cornerRadiusTL = topStart,
                smoothnessAsPercentTL = GROUP_SMOOTHNESS,
                cornerRadiusTR = topEnd,
                smoothnessAsPercentTR = GROUP_SMOOTHNESS,
                cornerRadiusBL = bottomStart,
                smoothnessAsPercentBL = GROUP_SMOOTHNESS,
                cornerRadiusBR = bottomEnd,
                smoothnessAsPercentBR = GROUP_SMOOTHNESS,
            )
        } else {
            RoundedCornerShape(topStart = topStart, topEnd = topEnd, bottomStart = bottomStart, bottomEnd = bottomEnd)
        }
    }
}
