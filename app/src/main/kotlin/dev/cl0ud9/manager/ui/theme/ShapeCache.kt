package dev.cl0ud9.manager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

// Settings > Appearance's "smooth corners" toggle - off swaps every ShapeCache shape for a plain
// rounded-corner equivalent at the same radius, cheaper to draw on low-end devices. Defaults to on
val LocalUseSmoothCorners: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

@Composable
fun ProvideUseSmoothCorners(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalUseSmoothCorners provides enabled, content = content)
}

// squircle corners for the expressive look, cached to avoid recomputing the path per composition -
// 60% smoothness throughout, with smoothPill for the floating nav bar and pill-shaped buttons. Each
// shape is a composable getter (same pattern as MaterialTheme.colorScheme) rather than a plain val, so every existing
// `ShapeCache.smoothNN` call site keeps working unchanged while still reading the toggle above
object ShapeCache {
    private val Smooth4 = AbsoluteSmoothCornerShape(cornerRadius = 4.dp, smoothnessAsPercent = 60)
    private val Smooth8 = AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 60)
    private val Smooth10 = AbsoluteSmoothCornerShape(cornerRadius = 10.dp, smoothnessAsPercent = 60)
    private val Smooth12 = AbsoluteSmoothCornerShape(cornerRadius = 12.dp, smoothnessAsPercent = 60)
    private val Smooth14 = AbsoluteSmoothCornerShape(cornerRadius = 14.dp, smoothnessAsPercent = 60)
    private val Smooth16 = AbsoluteSmoothCornerShape(cornerRadius = 16.dp, smoothnessAsPercent = 60)
    private val Smooth20 = AbsoluteSmoothCornerShape(cornerRadius = 20.dp, smoothnessAsPercent = 60)
    private val Smooth24 = AbsoluteSmoothCornerShape(cornerRadius = 24.dp, smoothnessAsPercent = 60)
    private val Smooth28 = AbsoluteSmoothCornerShape(cornerRadius = 28.dp, smoothnessAsPercent = 60)
    private val Smooth32 = AbsoluteSmoothCornerShape(cornerRadius = 32.dp, smoothnessAsPercent = 60)
    private val SmoothPill = AbsoluteSmoothCornerShape(cornerRadius = 50.dp, smoothnessAsPercent = 60)

    private val Plain4 = RoundedCornerShape(4.dp)
    private val Plain8 = RoundedCornerShape(8.dp)
    private val Plain10 = RoundedCornerShape(10.dp)
    private val Plain12 = RoundedCornerShape(12.dp)
    private val Plain14 = RoundedCornerShape(14.dp)
    private val Plain16 = RoundedCornerShape(16.dp)
    private val Plain20 = RoundedCornerShape(20.dp)
    private val Plain24 = RoundedCornerShape(24.dp)
    private val Plain28 = RoundedCornerShape(28.dp)
    private val Plain32 = RoundedCornerShape(32.dp)
    private val PlainPill = RoundedCornerShape(50.dp)

    val smooth4: Shape @Composable get() = if (LocalUseSmoothCorners.current) Smooth4 else Plain4
    val smooth8: Shape @Composable get() = if (LocalUseSmoothCorners.current) Smooth8 else Plain8
    val smooth10: Shape @Composable get() = if (LocalUseSmoothCorners.current) Smooth10 else Plain10
    val smooth12: Shape @Composable get() = if (LocalUseSmoothCorners.current) Smooth12 else Plain12
    val smooth14: Shape @Composable get() = if (LocalUseSmoothCorners.current) Smooth14 else Plain14
    val smooth16: Shape @Composable get() = if (LocalUseSmoothCorners.current) Smooth16 else Plain16
    val smooth20: Shape @Composable get() = if (LocalUseSmoothCorners.current) Smooth20 else Plain20
    val smooth24: Shape @Composable get() = if (LocalUseSmoothCorners.current) Smooth24 else Plain24
    val smooth28: Shape @Composable get() = if (LocalUseSmoothCorners.current) Smooth28 else Plain28
    val smooth32: Shape @Composable get() = if (LocalUseSmoothCorners.current) Smooth32 else Plain32
    val smoothPill: Shape @Composable get() = if (LocalUseSmoothCorners.current) SmoothPill else PlainPill

    // for a radius that isn't one of the fixed sizes above (the nav bar's user-adjustable corner
    // radius slider) - remembered keyed on the radius and the toggle, rather than rebuilt on every
    // recomposition of whatever's calling this, which for a screen-level shape like this one can be
    // every frame of an unrelated animation (the tab depth effect, a scroll) with the radius unchanged
    @Composable
    fun corner(radius: Dp): Shape {
        val useSmoothCorners = LocalUseSmoothCorners.current
        return remember(radius, useSmoothCorners) {
            if (useSmoothCorners) {
                AbsoluteSmoothCornerShape(cornerRadius = radius, smoothnessAsPercent = 60)
            } else {
                RoundedCornerShape(radius)
            }
        }
    }

    // the content panel sitting under a screen's header strip - rounded only at the top, square at
    // the bottom since it runs all the way to the edge of the screen behind the floating nav bar
    @Composable
    fun contentPanel(radius: Dp): Shape {
        val useSmoothCorners = LocalUseSmoothCorners.current
        return remember(radius, useSmoothCorners) {
            if (useSmoothCorners) {
                AbsoluteSmoothCornerShape(
                    cornerRadiusTL = radius,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusTR = radius,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusBL = 0.dp,
                    smoothnessAsPercentBL = 60,
                    cornerRadiusBR = 0.dp,
                    smoothnessAsPercentBR = 60,
                )
            } else {
                RoundedCornerShape(topStart = radius, topEnd = radius, bottomStart = 0.dp, bottomEnd = 0.dp)
            }
        }
    }

    // MaterialExpressiveTheme's `shapes` param needs CornerBasedShape specifically (for its shape
    // interpolation), not the generic Shape the properties above are typed as - built here, once,
    // from the same private smooth/plain shape objects, instead of widening them at the call site
    @Composable
    fun materialShapes(): Shapes =
        if (LocalUseSmoothCorners.current) {
            Shapes(extraSmall = Smooth8, small = Smooth12, medium = Smooth16, large = Smooth28, extraLarge = Smooth32)
        } else {
            Shapes(extraSmall = Plain8, small = Plain12, medium = Plain16, large = Plain28, extraLarge = Plain32)
        }
}
