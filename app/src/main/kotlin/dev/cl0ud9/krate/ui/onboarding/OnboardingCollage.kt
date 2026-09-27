@file:Suppress("MagicNumber")

package dev.cl0ud9.krate.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.theme.ShapeCache

private val COLLAGE_HEIGHT = 220.dp

// where one tile of the collage sits, how big it is and how it's turned
private class CollageSlot(
    val size: Float,
    val align: Alignment,
    val rotation: Float,
    val dx: Float,
    val dy: Float,
)

// big centre tile, then the four around it: top start, bottom end, top end, bottom start
private val SLOTS =
    listOf(
        CollageSlot(0.8f, Alignment.Center, -15f, 0f, 0f),
        CollageSlot(0.4f, Alignment.TopStart, 15f, 0.05f, 0.05f),
        CollageSlot(0.4f, Alignment.BottomEnd, 5f, -0.05f, -0.05f),
        CollageSlot(0.5f, Alignment.TopEnd, -20f, -0.1f, 0.1f),
        CollageSlot(0.35f, Alignment.BottomStart, 10f, 0.1f, -0.1f),
    )

// a loose, tilted pile of the step's icons: the main one big in the middle, the rest scattered around it
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OnboardingCollage(
    @DrawableRes icons: List<Int>,
    modifier: Modifier = Modifier,
    handoff: MarkHandoff? = null,
) {
    val colors = MaterialTheme.colorScheme
    val tones =
        listOf(
            colors.surfaceContainerHigh to colors.primary,
            colors.surfaceContainerHigh to colors.onSurfaceVariant,
            colors.surfaceContainerHigh to colors.tertiary,
            colors.surfaceContainerHigh to colors.onSurfaceVariant,
            colors.surfaceContainerHighest to colors.secondary,
        )
    val shapes: List<Shape> =
        listOf(
            ShapeCache.smooth32,
            CircleShape,
            CircleShape,
            ShapeCache.smooth24,
            MaterialShapes.Cookie9Sided.toShape(),
        )
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(COLLAGE_HEIGHT)) {
        val base: Dp = minOf(maxHeight, COLLAGE_HEIGHT)
        icons.take(SLOTS.size).forEachIndexed { index, icon ->
            val slot = SLOTS[index]
            val (container, glyph) = tones[index]
            val handedOff = handoff != null && icon == R.drawable.ic_krate
            CollageTile(
                icon = icon,
                size = base * slot.size,
                shape = shapes[index],
                container = container,
                glyph = glyph,
                modifier =
                    Modifier
                        .align(
                            slot.align,
                        ).offset(x = maxWidth * slot.dx, y = maxHeight * slot.dy)
                        // measured before the tilt, so the intro gets the tile's true size and centre
                        .then(
                            if (handedOff) {
                                Modifier.onGloballyPositioned {
                                    handoff?.onPlaced(
                                        it.boundsInRoot(),
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ).alpha(if (handedOff && handoff?.hidden == true) 0f else 1f)
                        .rotate(slot.rotation),
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun CollageTile(
    @DrawableRes icon: Int,
    size: Dp,
    shape: Shape,
    container: Color,
    glyph: Color,
    modifier: Modifier,
) {
    Surface(modifier = modifier.size(size), shape = shape, color = container) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = glyph, modifier = Modifier.size(size * 0.5f))
        }
    }
}
