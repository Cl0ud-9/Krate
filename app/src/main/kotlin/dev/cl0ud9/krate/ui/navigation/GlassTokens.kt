package dev.cl0ud9.krate.ui.navigation

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

// every number Krate's liquid glass is drawn with, in one place so the pieces stay one material
internal object GlassTokens {
    // the frosting: what's behind, made more colourful twice over, a little brighter, then blurred; a press blurs it a
    // touch more
    val Blur = 8.dp
    val PressBlur = 2.dp
    const val SATURATION = 1.5f
    const val BRIGHTNESS = 0.05f

    // the shade over the frosting: black in dark themes, white in light ones, never a colour
    const val SHADE = 0.272f

    // how far in from a floating piece's edge it bends light, and how strongly: a share of its shorter side, a little
    // deeper while pressed. A header is far taller than any floating piece, so it bends only as much as the bar does
    const val LENS_HEIGHT_SHARE = 0.25f
    const val LENS_AMOUNT_SHARE = 0.5f
    val PressLens = 2.dp
    val HeaderLensHeight = 16.dp
    val HeaderLensAmount = 32.dp

    // the hairline where edge-to-edge glass meets the content
    val EdgeWidth = 1.dp
    const val EDGE_DARK = 0.22f
    const val EDGE_LIGHT = 0.7f

    // the light round a solid card's rim while glass is on, and how much of it is left at the far corner
    const val RIM_DARK = 0.13f
    const val RIM_LIGHT = 0.5f
    const val RIM_FAR_SHARE = 0.35f

    // App Details' top card: the app's icon blown up and blurred behind it, faintly, so it reads as glass tinted by
    // the app
    val IconFrostBlur = 40.dp
    const val ICON_FROST_SCALE = 1.4f
    const val ICON_FROST_DARK = 0.35f
    const val ICON_FROST_LIGHT = 0.3f

    // the navigation bar's selected pill: the size of Material's indicator, swelling into a lens while pressed
    val PillWidth = 64.dp
    val PillHeight = 32.dp
    const val PILL_REST_ALPHA = 0.85f

    // the pill as clear glass: how tinted it is at rest, and how much of its bend and rim light it has then
    const val PILL_GLASS_TINT = 0.28f
    const val PILL_REST_SHINE = 0.07f
    const val PILL_REST_SHARE = 0.5f
    const val PILL_PRESSED_TINT = 0.45f
    const val PILL_PRESSED_SHINE = 0.08f
    const val PILL_PRESSED_SCALE = 1.3f
    val PillLensHeight = 8.dp
    val PillLensAmount = 12.dp
    val PillInnerShadow = 6.dp

    // the space a navigation label takes under its icon: its gap and one line of 13sp text
    val LabelGap = 8.dp
    val LabelLine = 16.sp

    // how much a glass piece grows under a touch: small buttons a lot, wide bars and messages a little
    const val PRESS_SCALE_SMALL = 1.12f
    const val PRESS_SCALE_WIDE = 1.04f

    // a quick slide stretches the pill along its way, up to this much
    const val STRETCH_VELOCITY = 10f
    const val STRETCH_X = 0.75f
    const val STRETCH_Y = 0.25f
    const val STRETCH_LIMIT = 0.2f

    // the light under a finger on the glass, and how far it spreads, as a share of the piece's shorter side
    const val PRESS_GLOW = 0.18f
    const val PRESS_GLOW_SPREAD = 1.2f

    // liquid glass switching on or off: the materials cross-fade, the layout never moves
    const val TOGGLE_FADE_MS = 300
}

// the shared materials a piece of glass can name instead of spelling out its numbers
enum class GlassKind { PLAIN, BAR, FLOATING }

// how one kind of glass piece is drawn. Every piece shares the frosting, the bend and the rim; only the numbers differ
data class GlassLook(
    val blur: Dp,
    val saturation: Float,
    val brightness: Float,
    // the shade over the frosting
    val shade: Float,
    // how far in from the edge the bend reaches and how strongly, as shares of the piece's shorter side
    val lensHeightShare: Float,
    val lensAmountShare: Float,
    // thicker glass: the bend deepens towards the edge, and splits the colours along it like real glass does
    val depth: Boolean,
    val chromatic: Boolean,
    // the light along the rim, as a multiple of the usual
    val highlight: Float,
    // the glass lifting off what's behind, and its thickness seen from inside
    val shadowRadius: Dp,
    val shadowAlpha: Float,
    val innerRadius: Dp,
    val innerAlpha: Float,
    // a faint light across the top, fading down
    val sheen: Float = 0f,
    // the rim light gathers at the corners as it falls off faster; 1 is the usual spread
    val rimFalloff: Float = 1f,
    // thickness: a light line just inside the edge instead of a dark one, and the bottom darkened a little
    val innerLight: Boolean = false,
    val bottomShade: Float = 0f,
    // which shared material this stands for, resolved by theme where it's drawn
    val kind: GlassKind = GlassKind.PLAIN,
) {
    // the look for a dark or a light page: the shared materials have one of each, any other look is its own
    fun forTheme(dark: Boolean): GlassLook =
        when (kind) {
            GlassKind.PLAIN -> this
            GlassKind.BAR -> if (dark) BarDark else BarLight
            GlassKind.FLOATING -> if (dark) FloatingDark else FloatingLight
        }

    // the rim light, gathered at the corners when it falls off faster
    fun rim(strength: Float): Highlight =
        Highlight.Default.copy(
            alpha = strength * highlight,
            style = if (rimFalloff == 1f) HighlightStyle.Default else HighlightStyle.Default(falloff = rimFalloff),
        )

    fun lift(strength: Float): Shadow = Shadow(radius = shadowRadius, alpha = shadowAlpha * strength)

    fun thickness(strength: Float): InnerShadow {
        val inner = InnerShadow(radius = innerRadius, alpha = innerAlpha * strength)
        return if (innerLight) inner.copy(color = Color.White) else inner
    }

    // what's drawn over the frosting: the shade, a faint light across the top, the bottom darkened a little
    fun DrawScope.drawSurface(
        shade: Color,
        strength: Float,
    ) {
        drawRect(shade, alpha = this@GlassLook.shade * strength)
        if (sheen > 0f) {
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = sheen * strength), Color.Transparent)))
        }
        if (bottomShade > 0f) {
            val dim = Color.Black.copy(alpha = bottomShade * strength)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, dim)))
        }
    }

    companion object {
        // headers, messages and sheets
        val Standard =
            GlassLook(
                blur = GlassTokens.Blur,
                saturation = GlassTokens.SATURATION,
                brightness = GlassTokens.BRIGHTNESS,
                shade = GlassTokens.SHADE,
                lensHeightShare = GlassTokens.LENS_HEIGHT_SHARE,
                lensAmountShare = GlassTokens.LENS_AMOUNT_SHARE,
                depth = false,
                chromatic = false,
                highlight = 1f,
                shadowRadius = 0.dp,
                shadowAlpha = 0f,
                innerRadius = 0.dp,
                innerAlpha = 0f,
            )

        // the shared materials, named by what they're for; each resolves to its dark or light look where it's drawn
        val Bar = Standard.copy(kind = GlassKind.BAR)
        val Floating = Standard.copy(kind = GlassKind.FLOATING)

        // the selected tab's pill: only lightly frosted, so what's behind it shows through
        val Pill = Standard.copy(blur = 3.dp, saturation = 1.4f, brightness = 0.06f)

        // the floating navigation bar over a dark page: the usual tint and colour, but clearer (less blur), with the
        // rim light gathered at the corners, a light line just inside the top edge, the bottom shaded a little for
        // thickness, and a soft shadow lifting it off the page
        val BarDark =
            GlassLook(
                blur = 5.dp,
                saturation = GlassTokens.SATURATION,
                brightness = GlassTokens.BRIGHTNESS,
                shade = GlassTokens.SHADE,
                lensHeightShare = GlassTokens.LENS_HEIGHT_SHARE,
                lensAmountShare = GlassTokens.LENS_AMOUNT_SHARE,
                depth = true,
                chromatic = false,
                highlight = 1.5f,
                shadowRadius = 22.dp,
                shadowAlpha = 0.3f,
                innerRadius = 3.dp,
                innerAlpha = 0.28f,
                rimFalloff = 2.2f,
                innerLight = true,
                bottomShade = 0.14f,
            )

        // over a light page a white glass can't be lit from inside, so its edge stays dark and its shadow carries it
        val BarLight =
            GlassLook(
                blur = 5.dp,
                saturation = GlassTokens.SATURATION,
                brightness = GlassTokens.BRIGHTNESS,
                shade = GlassTokens.SHADE,
                lensHeightShare = GlassTokens.LENS_HEIGHT_SHARE,
                lensAmountShare = GlassTokens.LENS_AMOUNT_SHARE,
                depth = true,
                chromatic = false,
                highlight = 1.3f,
                shadowRadius = 22.dp,
                shadowAlpha = 0.2f,
                innerRadius = 3.dp,
                innerAlpha = 0.1f,
                rimFalloff = 2.2f,
                bottomShade = 0.05f,
            )

        // everything else that floats and carries text (the scrolled header, messages, buttons): the bar's material, as
        // blurry as before so the words stay easy to read over whatever slides under them
        val FloatingDark = BarDark.copy(blur = GlassTokens.Blur)
        val FloatingLight = BarLight.copy(blur = GlassTokens.Blur)
    }
}
