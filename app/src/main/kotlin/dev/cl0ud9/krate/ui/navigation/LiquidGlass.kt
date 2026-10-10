package dev.cl0ud9.krate.ui.navigation

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import dev.cl0ud9.krate.domain.repository.SettingsRepository
import dev.cl0ud9.krate.ui.theme.krateGlow

// blur needs Android 12's RenderEffect; the light-bending edge (lens) needs Android 13's runtime shaders
val liquidGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val lensSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private const val HALF = 0.5f

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

// records this content for the glass over it, on the colour it sits on: without it, whatever sits straight on the
// page (section titles, dates) records with nothing around it, and stays sharp through the glass
fun Modifier.glassSource(
    backdrop: LayerBackdrop?,
    background: Color,
): Modifier = if (backdrop == null) this else layerBackdrop(backdrop).background(background)

// what's behind, blurred, more colourful and bent at the edges, under a light black (or, in light themes, white)
// shade; nothing at all when glass is off
@Suppress("LongParameterList")
@Composable
fun Modifier.liquidGlass(
    backdrop: LayerBackdrop?,
    shape: Shape,
    edge: GlassEdge,
    cornerRadius: Dp = 0.dp,
    // extra drawing-layer changes, like the swell of the navigation bar under a touch
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    // how much of the glass to draw, read while drawing: a header ramps it in as content slides under
    strength: () -> Float = { 1f },
    // how hard it's pressed, read while drawing: a pressed piece blurs and bends a little more
    press: () -> Float = { 0f },
    // a tall piece bends no more than the bar does
    header: Boolean = false,
): Modifier {
    if (backdrop == null) return this
    // Krate's own theme, which can differ from the phone's
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF
    val shade = if (dark) Color.Black else Color.White
    val light = Color.White.copy(alpha = if (dark) GlassTokens.EDGE_DARK else GlassTokens.EDGE_LIGHT)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            frost(strength(), press())
            // only floating glass bends light at its sides: a full-width bar runs into the screen's edges, where
            // bending would warp what's behind into curves
            if (lensSupported && edge == GlassEdge.ALL && shape is CornerBasedShape) bend(header, press())
        },
        layerBlock = layerBlock,
        highlight = if (edge == GlassEdge.ALL) ({ Highlight.Default.copy(alpha = strength()) }) else null,
        onDrawSurface = { drawRect(shade, alpha = GlassTokens.SHADE * strength()) },
        onDrawFront =
            when (edge) {
                GlassEdge.ALL -> null
                GlassEdge.TOP -> (
                    {
                        drawTopEdge(
                            shape,
                            cornerRadius.toPx(),
                            light.copy(alpha = light.alpha * strength()),
                        )
                    }
                )
                GlassEdge.BOTTOM -> ({ drawBottomEdge(light.copy(alpha = light.alpha * strength())) })
            },
    )
}

// Krate's frosting: what's behind, more colourful twice over and a little brighter, then blurred. Every glass piece
// uses it, so a piece sitting on another reads as the same material. A header ramps it in with its strength, so while
// open it matches the page exactly instead of a lighter, bluer band
internal fun BackdropEffectScope.frost(
    strength: Float = 1f,
    press: Float = 0f,
) {
    val saturation = lerp(1f, GlassTokens.SATURATION, strength)
    colorControls(saturation = saturation)
    colorControls(brightness = GlassTokens.BRIGHTNESS * strength, saturation = saturation)
    blur(GlassTokens.Blur.toPx() + GlassTokens.PressBlur.toPx() * press)
}

// bends what's behind near the edges: a quarter of the piece's shorter side in, by half of it
private fun BackdropEffectScope.bend(
    header: Boolean,
    press: Float,
) {
    val shorter = size.minDimension
    var height = shorter * GlassTokens.LENS_HEIGHT_SHARE
    var amount = shorter * GlassTokens.LENS_AMOUNT_SHARE
    if (header) {
        height = height.coerceAtMost(GlassTokens.HeaderLensHeight.toPx())
        amount = amount.coerceAtMost(GlassTokens.HeaderLensAmount.toPx())
    }
    lens(height + GlassTokens.PressLens.toPx() * press, amount)
}

// the shape's outline, drawn only as far down as its top corners reach
private fun DrawScope.drawTopEdge(
    shape: Shape,
    cornerPx: Float,
    color: Color,
) {
    val width = GlassTokens.EdgeWidth.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    clipRect(bottom = cornerPx + width) {
        drawOutline(outline, color = color, style = Stroke(width = width))
    }
}

private fun DrawScope.drawBottomEdge(color: Color) {
    val width = GlassTokens.EdgeWidth.toPx()
    val y = size.height - width / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = width)
}
