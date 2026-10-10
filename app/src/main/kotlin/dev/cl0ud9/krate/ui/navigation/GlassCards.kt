package dev.cl0ud9.krate.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale

private const val HALF = 0.5f

// a card's glass edge while glass is on: a thin light round its rim, brightest at the top left where light falls and
// again faintly at the bottom right, like the rim on Krate's floating glass. The card stays solid, so text stays crisp
@Composable
fun Modifier.glassRim(shape: Shape): Modifier {
    if (!LocalLiquidGlass.current) return this
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF
    val near = Color.White.copy(alpha = if (dark) GlassTokens.RIM_DARK else GlassTokens.RIM_LIGHT)
    val far = near.copy(alpha = near.alpha * GlassTokens.RIM_FAR_SHARE)
    return drawWithCache {
        val width = GlassTokens.EdgeWidth.toPx()
        val inner = Size(size.width - width, size.height - width)
        val outline = shape.createOutline(inner, layoutDirection, this)
        val light =
            Brush.linearGradient(
                listOf(near, Color.Transparent, far),
                Offset.Zero,
                Offset(size.width, size.height),
            )
        onDrawWithContent {
            drawContent()
            translate(width / 2, width / 2) { drawOutline(outline, light, style = Stroke(width)) }
        }
    }
}

// the same rim as a border, for Material pieces that take one, like a menu; null with glass off
@Composable
fun rememberGlassRimBorder(): BorderStroke? {
    if (!LocalLiquidGlass.current) return null
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF
    val near = Color.White.copy(alpha = if (dark) GlassTokens.RIM_DARK else GlassTokens.RIM_LIGHT)
    val far = near.copy(alpha = near.alpha * GlassTokens.RIM_FAR_SHARE)
    return remember(near, far) {
        BorderStroke(GlassTokens.EdgeWidth, Brush.linearGradient(listOf(near, Color.Transparent, far)))
    }
}

// an icon blown up and blurred behind a card's content, so the card reads as frosted glass tinted by that app
@Composable
fun BoxScope.IconFrost(icon: ImageBitmap?) {
    if (!LocalLiquidGlass.current || icon == null) return
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF
    Image(
        bitmap = icon,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(GlassTokens.SATURATION) }),
        modifier =
            Modifier.matchParentSize().graphicsLayer {
                scaleX = GlassTokens.ICON_FROST_SCALE
                scaleY = GlassTokens.ICON_FROST_SCALE
                alpha = if (dark) GlassTokens.ICON_FROST_DARK else GlassTokens.ICON_FROST_LIGHT
                val radius = GlassTokens.IconFrostBlur.toPx()
                renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
            },
    )
}
