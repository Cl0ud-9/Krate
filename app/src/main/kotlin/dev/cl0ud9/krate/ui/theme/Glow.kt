package dev.cl0ud9.krate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private const val DARK_LUMINANCE_THRESHOLD = 0.5f

// how strongly each pool of colour shows; a dark surface needs more to read as the same weight
private const val DARK_FIRST = 0.3f
private const val DARK_SECOND = 0.2f
private const val LIGHT_FIRST = 0.16f
private const val LIGHT_SECOND = 0.12f

// where the pools sit and how far they spread, as fractions of the box
private const val FIRST_X = 0.12f
private const val FIRST_REACH = 0.8f
private const val SECOND_REACH = 0.6f

// two soft pools of the theme's colours, spilling in from the top-left and bottom-right corners; strength scales
// both, so a card already filled with colour can take a gentler version
@Composable
fun Modifier.krateGlow(strength: Float = 1f): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < DARK_LUMINANCE_THRESHOLD
    val first = colors.primary.copy(alpha = (if (dark) DARK_FIRST else LIGHT_FIRST) * strength)
    val second = colors.tertiary.copy(alpha = (if (dark) DARK_SECOND else LIGHT_SECOND) * strength)
    return drawWithCache {
        val reach = size.maxDimension
        val topLeft =
            Brush.radialGradient(
                listOf(first, Color.Transparent),
                center = Offset(size.width * FIRST_X, 0f),
                radius = reach * FIRST_REACH,
            )
        val bottomRight =
            Brush.radialGradient(
                listOf(second, Color.Transparent),
                center = Offset(size.width, size.height),
                radius = reach * SECOND_REACH,
            )
        onDrawBehind {
            drawRect(topLeft)
            drawRect(bottomRight)
        }
    }
}
