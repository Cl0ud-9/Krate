package dev.cl0ud9.manager.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.ui.components.LocalIntroHeaderSlot
import dev.cl0ud9.manager.ui.theme.ManagerHeroTitle
import dev.cl0ud9.manager.ui.util.isShortScreen
import kotlin.math.roundToInt

private val HeaderStartInset = 24.dp

// a tab's header: big title (led by the Krate mark on Home), its actions, and a one-line subtitle, all on one left edge
@Composable
internal fun TabHeader(
    title: String,
    icon: Painter?,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit,
) {
    // a phone on its side keeps the header tight and drops the subtitle, leaving the height to the content
    val short = isShortScreen()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = if (short) 4.dp else 20.dp, bottom = if (short) 10.dp else 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = HeaderStartInset, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabTitle(title = title, icon = icon, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
        if (subtitle != null && !short) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = HeaderStartInset, end = 24.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun TabTitle(
    title: String,
    icon: Painter?,
    modifier: Modifier = Modifier,
) {
    val introSlot = LocalIntroHeaderSlot.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .then(if (introSlot != null) Modifier.introSlot(introSlot) else Modifier)
                        .padding(end = 10.dp)
                        .size(34.dp),
            )
        }
        Text(
            text = title,
            style = ManagerHeroTitle,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// during the intro the header mark is hidden and its space opens up, so the flying mark lands in a gap made for it
private fun Modifier.introSlot(openness: () -> Float): Modifier =
    graphicsLayer { alpha = 0f }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val width = (placeable.width * openness()).roundToInt()
            layout(width, placeable.height) { placeable.placeRelative(0, 0) }
        }
