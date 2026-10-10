package dev.cl0ud9.krate.ui.navigation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    const val RIM_DARK = 0.22f
    const val RIM_LIGHT = 0.8f
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
