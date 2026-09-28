package dev.cl0ud9.krate.ui.util

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import dev.cl0ud9.krate.ui.theme.ShapeCache

private val HIGHLIGHT_BLEED = 8.dp
private val HIGHLIGHT_VERTICAL = 6.dp

// a tappable row inside a card: its press highlight is rounded and reaches a little past the row into the card's
// padding, while the row's content stays lined up with the rest of the card
@Composable
internal fun Modifier.tappableRow(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return layout { measurable, constraints ->
        val extra = (HIGHLIGHT_BLEED * 2).roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = extra))
        layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
    }.pressScale(interactionSource)
        .clip(ShapeCache.rounded12)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick,
        ).padding(horizontal = HIGHLIGHT_BLEED, vertical = HIGHLIGHT_VERTICAL)
}
