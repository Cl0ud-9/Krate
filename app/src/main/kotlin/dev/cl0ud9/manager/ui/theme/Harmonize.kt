package dev.cl0ud9.manager.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

private const val MAX_HUE_SHIFT = 15f
private const val HSL_PARTS = 3
private const val HUE_PULL = 0.5f
private const val FULL_CIRCLE = 360f
private const val HALF_CIRCLE = 180f

// nudges a fixed color's hue toward the theme's primary (half the gap, at most 15 degrees), as Material advises
fun Color.harmonizedWith(primary: Color): Color {
    val hsl = FloatArray(HSL_PARTS).also { ColorUtils.colorToHSL(toArgb(), it) }
    val target = FloatArray(HSL_PARTS).also { ColorUtils.colorToHSL(primary.toArgb(), it) }
    val gap = ((target[0] - hsl[0] + FULL_CIRCLE + HALF_CIRCLE) % FULL_CIRCLE) - HALF_CIRCLE
    hsl[0] = (hsl[0] + sign(gap) * min(abs(gap) * HUE_PULL, MAX_HUE_SHIFT) + FULL_CIRCLE) % FULL_CIRCLE
    return Color(ColorUtils.HSLToColor(hsl)).copy(alpha = alpha)
}
