package dev.cl0ud9.krate.ui.navigation

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import dev.cl0ud9.krate.domain.repository.SettingsRepository
import dev.cl0ud9.krate.ui.theme.krateGlow

// blur needs Android 12's RenderEffect; the light-bending edge (lens) needs Android 13's runtime shaders
val liquidGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val lensSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private val GLASS_BLUR = 14.dp
private val LENS_HEIGHT = 16.dp
private val LENS_AMOUNT = 28.dp

// tint over the frosted backdrop: enough for the icons and labels to stay readable over any screen behind it
private const val GLASS_TINT_DARK = 0.55f
private const val GLASS_TINT_LIGHT = 0.62f
private const val HALF = 0.5f
private val TOP_EDGE_WIDTH = 1.dp
private const val TOP_EDGE_DARK = 0.22f
private const val TOP_EDGE_LIGHT = 0.7f

// the Appearance switch, honoured only where the phone can draw the glass
@Composable
internal fun rememberLiquidGlass(
    settings: SettingsRepository,
    saved: Boolean,
): Boolean =
    settings.observeLiquidGlass().collectAsStateWithLifecycle(initialValue = saved).value && liquidGlassSupported

// whether Krate draws liquid glass right now: the Appearance switch, on a phone that can
val LocalLiquidGlass = staticCompositionLocalOf { false }

// which of a glass piece's edges catch the light: all round when it floats, only the one facing the content when it
// runs into the screen's sides (a full-width bar's top, a header's bottom)
enum class GlassEdge {
    ALL,
    TOP,
    BOTTOM,
}

// a screen's main card, in the tab header's glowing material while glass is on; plain otherwise
@Composable
fun Modifier.heroGlow(): Modifier = if (LocalLiquidGlass.current) krateGlow() else this

// a page's own scrolling content, recorded for the glass floating over it; null while glass is off
@Composable
fun rememberPageGlass(): LayerBackdrop? = if (LocalLiquidGlass.current) rememberLayerBackdrop() else null

// records this content for the glass over it
fun Modifier.glassSource(backdrop: LayerBackdrop?): Modifier = if (backdrop == null) this else layerBackdrop(backdrop)

// what's behind, blurred, saturated a little and bent at the edges, under a light tint of the piece's own colour;
// nothing at all when glass is off
@Composable
fun Modifier.liquidGlass(
    backdrop: LayerBackdrop?,
    shape: Shape,
    edge: GlassEdge,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    cornerRadius: Dp = 0.dp,
): Modifier {
    if (backdrop == null) return this
    // Krate's own theme, which can differ from the phone's
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF
    val tint = color.copy(alpha = if (dark) GLASS_TINT_DARK else GLASS_TINT_LIGHT)
    val light = Color.White.copy(alpha = if (dark) TOP_EDGE_DARK else TOP_EDGE_LIGHT)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(GLASS_BLUR.toPx())
            // the bent edge takes only corner-based shapes; any other just goes without it
            if (lensSupported && shape is CornerBasedShape) lens(LENS_HEIGHT.toPx(), LENS_AMOUNT.toPx())
        },
        highlight = if (edge == GlassEdge.ALL) ({ Highlight.Default }) else null,
        onDrawSurface = { drawRect(tint) },
        onDrawFront =
            when (edge) {
                GlassEdge.ALL -> null
                GlassEdge.TOP -> ({ drawTopEdge(shape, cornerRadius.toPx(), light) })
                GlassEdge.BOTTOM -> ({ drawBottomEdge(light) })
            },
    )
}

// the shape's outline, drawn only as far down as its top corners reach
private fun DrawScope.drawTopEdge(
    shape: Shape,
    cornerPx: Float,
    color: Color,
) {
    val width = TOP_EDGE_WIDTH.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    clipRect(bottom = cornerPx + width) {
        drawOutline(outline, color = color, style = Stroke(width = width))
    }
}

private fun DrawScope.drawBottomEdge(color: Color) {
    val width = TOP_EDGE_WIDTH.toPx()
    val y = size.height - width / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = width)
}
