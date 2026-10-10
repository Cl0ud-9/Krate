package dev.cl0ud9.krate.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.max

// how small the icon is read at, how its colours are grouped, and the least colour an icon needs to have one at all
private const val SAMPLE = 24
private const val HUE_BUCKETS = 12
private const val FULL_HUE = 360f
private const val MIN_VIVIDNESS = 3f
private const val OPAQUE = 128

// a colour has to stand at least this far apart from what it sits on, Android's bar for graphics
private const val MIN_CONTRAST = 3.0
private const val LIGHTNESS_STEP = 0.04f

// the most vivid colour in an app's icon, made to stand out against [behind]; null for an icon with no real colour,
// like a grey or white one, so the caller keeps its own
@Composable
fun rememberIconAccent(
    icon: ImageBitmap?,
    behind: Color,
): Color? =
    remember(icon, behind) {
        // a bitmap that can't be read just means no accent
        icon?.let { runCatching { vividColour(it) }.getOrNull() }?.let { contrasted(it, behind) }
    }

private fun vividColour(icon: ImageBitmap): Color? {
    val source = icon.asAndroidBitmap()
    val readable = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
    val small = Bitmap.createScaledBitmap(readable, SAMPLE, SAMPLE, true)
    val weight = FloatArray(HUE_BUCKETS)
    val reds = FloatArray(HUE_BUCKETS)
    val greens = FloatArray(HUE_BUCKETS)
    val blues = FloatArray(HUE_BUCKETS)
    val hsv = FloatArray(HSL_PARTS)
    for (y in 0 until SAMPLE) {
        for (x in 0 until SAMPLE) {
            val pixel = small.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < OPAQUE) continue
            android.graphics.Color.colorToHSV(pixel, hsv)
            // saturated and bright counts most; greys, black and white hardly at all
            val vivid = hsv[1] * hsv[2]
            val bucket = ((hsv[0] / FULL_HUE) * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
            weight[bucket] += vivid
            reds[bucket] += android.graphics.Color.red(pixel) * vivid
            greens[bucket] += android.graphics.Color.green(pixel) * vivid
            blues[bucket] += android.graphics.Color.blue(pixel) * vivid
        }
    }
    val best = weight.indices.maxBy { weight[it] }
    val total = weight[best]
    return if (total < MIN_VIVIDNESS) {
        null
    } else {
        Color(
            red = (reds[best] / total).toInt(),
            green = (greens[best] / total).toInt(),
            blue = (blues[best] / total).toInt(),
        )
    }
}

// lightens on a dark background and darkens on a light one, a step at a time, until it clears the contrast bar
private fun contrasted(
    colour: Color,
    behind: Color,
): Color {
    val back = behind.copy(alpha = 1f).toArgb()
    val hsl = FloatArray(HSL_PARTS)
    ColorUtils.colorToHSL(colour.toArgb(), hsl)
    val lighten = ColorUtils.calculateLuminance(back) < HALF_LUMINANCE
    var argb = colour.toArgb()
    var steps = 0
    while (ColorUtils.calculateContrast(argb, back) < MIN_CONTRAST && steps < MAX_STEPS) {
        hsl[2] = (if (lighten) hsl[2] + LIGHTNESS_STEP else max(0f, hsl[2] - LIGHTNESS_STEP)).coerceIn(0f, 1f)
        argb = ColorUtils.HSLToColor(hsl)
        steps++
    }
    return Color(argb)
}

private const val HALF_LUMINANCE = 0.5
private const val HSL_PARTS = 3
private const val MAX_STEPS = 25
