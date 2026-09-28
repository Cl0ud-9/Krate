package dev.cl0ud9.krate.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DEFAULT_FADE = 30.dp

// fades content out just above the screen's bottom edge and the system navigation bar, wherever the content sits: a
// sheet at its half-height peek runs past the screen's edge, where 3-button nav would draw its buttons over the text.
// Tracks its position in the draw phase only, so a sheet being dragged just redraws
@Composable
fun Modifier.fadeAboveSystemBar(
    color: Color,
    fade: Dp = DEFAULT_FADE,
): Modifier {
    val density = LocalDensity.current
    val barHeight = WindowInsets.navigationBars.getBottom(density).toFloat()
    val fadePx = with(density) { fade.toPx() }
    var top by remember { mutableFloatStateOf(0f) }
    var screenHeight by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    return this
        .onGloballyPositioned {
            top = it.positionInRoot().y
            screenHeight =
                it
                    .findRootCoordinates()
                    .size.height
                    .toFloat()
        }.drawWithContent {
            drawContent()
            // where the content stops being readable, in its own coordinates: its end, or the top of the system bar
            val clearUntil = minOf(size.height, screenHeight - barHeight - top)
            val coverUntil = minOf(size.height, screenHeight - top)
            if (clearUntil <= 0f) return@drawWithContent
            val fadeStart = (clearUntil - fadePx).coerceAtLeast(0f)
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, color), startY = fadeStart, endY = clearUntil),
                topLeft = Offset(0f, fadeStart),
                size = Size(size.width, clearUntil - fadeStart),
            )
            // behind the buttons themselves the sheet stays plain
            if (coverUntil > clearUntil) {
                drawRect(
                    color = color,
                    topLeft = Offset(0f, clearUntil),
                    size =
                        Size(
                            size.width,
                            coverUntil - clearUntil,
                        ),
                )
            }
        }
}
