package dev.cl0ud9.krate.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.roundToInt

private val PillShape = RoundedCornerShape(percent = 50)
private const val HALF = 0.5f

// how a liquid glass navigation bar moves: a glass pill that slides to the selected tab and stretches as it goes,
// swells under a touch and can be dragged to another tab, and a soft light that follows the finger
internal class GlassTabsMotion(
    val drag: DampedDragAnimation,
    val highlight: InteractiveHighlight,
    private val tabWidthPx: () -> Float,
) {
    val tabWidth: Float get() = tabWidthPx()

    // the whole bar grows a touch while pressed, and settles back with a little give
    val barLayer: GraphicsLayerScope.() -> Unit = { swell(GlassTokens.PRESS_SCALE_WIDE, highlight.progress) }

    val gestures: Modifier = drag.modifier.then(highlight.gestureModifier)
}

@Composable
internal fun rememberGlassTabsMotion(
    selectedIndex: Int,
    count: Int,
    onSelect: (Int) -> Unit,
): Pair<GlassTabsMotion, Modifier> {
    val scope = rememberCoroutineScope()
    var tabWidth by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableFloatStateOf(0f) }
    val select by rememberUpdatedState(onSelect)
    val selected by rememberUpdatedState(selectedIndex)
    val lastIndex = (count - 1).coerceAtLeast(1).toFloat()
    val motion =
        remember(scope, count) {
            lateinit var drag: DampedDragAnimation
            drag =
                DampedDragAnimation(
                    scope = scope,
                    initialValue = selectedIndex.toFloat(),
                    valueRange = 0f..lastIndex,
                    pressedScale = GlassTokens.PILL_PRESSED_SCALE,
                    // only a drag that starts on the pill moves it; a touch anywhere else just presses the glass
                    onDragStarted = { position ->
                        val start = drag.value * tabWidth
                        dragging = if (position.x in start..(start + tabWidth)) 1f else 0f
                    },
                    onDragStopped = {
                        if (dragging == 1f) {
                            val index = drag.targetValue.roundToInt().coerceIn(0, count - 1)
                            drag.animateToValue(index.toFloat())
                            if (index != selected) select(index)
                        }
                        dragging = 0f
                    },
                    onDrag = { amount ->
                        if (dragging == 1f) drag.updateValue(drag.targetValue + amount.x / tabWidth)
                    },
                )
            val highlight =
                InteractiveHighlight(scope) { size, _ ->
                    Offset((drag.value + HALF) * tabWidth, size.height * HALF)
                }
            GlassTabsMotion(drag, highlight) { tabWidth }
        }
    // a switch from a tap, or from going back, glides the pill over with the same little swell
    LaunchedEffect(motion, selectedIndex) {
        if (motion.drag.targetValue.roundToInt() != selectedIndex) motion.drag.animateToValue(selectedIndex.toFloat())
    }
    val measure = Modifier.onSizeChanged { tabWidth = it.width / count.toFloat().coerceAtLeast(1f) }
    return motion to measure
}

// the glass pill under the selected tab: tinted like Material's indicator at rest, clear glass that magnifies what's
// behind it while pressed or dragged
@Composable
internal fun GlassTabPill(
    motion: GlassTabsMotion,
    backdrop: LayerBackdrop,
    compact: Boolean,
) {
    val drag = motion.drag
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val restColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = GlassTokens.PILL_REST_ALPHA)
    val density = LocalDensity.current
    // the same size and place as the plain indicator: around the icon, which sits above the label
    val pillWidthPx = with(density) { GlassTokens.PillWidth.toPx() }
    val lift =
        if (compact) {
            0.dp
        } else {
            with(density) {
                (GlassTokens.LabelGap.toPx() + GlassTokens.LabelLine.toPx()).toDp() *
                    HALF
            }
        }
    Box(
        Modifier
            .fillMaxHeight()
            .wrapContentHeight(Alignment.CenterVertically)
            .offset(y = -lift)
            .graphicsLayer {
                val x = drag.value * motion.tabWidth + (motion.tabWidth - pillWidthPx) * HALF
                translationX = if (rtl) -x else x
            }.size(GlassTokens.PillWidth, GlassTokens.PillHeight)
            .pillGlass(drag, backdrop, restColor),
    )
}

// frosted like the bar at rest, so nothing behind shows through sharp; a lens with a rim and shadow while pressed,
// stretched along the way it's sliding
private fun Modifier.pillGlass(
    drag: DampedDragAnimation,
    backdrop: LayerBackdrop,
    restColor: Color,
): Modifier =
    drawBackdrop(
        backdrop = backdrop,
        shape = { PillShape },
        effects = {
            frost()
            val pressed = drag.pressProgress
            if (pressed > 0f) {
                lens(
                    GlassTokens.PillLensHeight.toPx() * pressed,
                    GlassTokens.PillLensAmount.toPx() * pressed,
                    chromaticAberration = true,
                )
            }
        },
        highlight = { Highlight.Default.copy(alpha = drag.pressProgress) },
        shadow = { Shadow(alpha = drag.pressProgress) },
        innerShadow = {
            InnerShadow(
                radius = GlassTokens.PillInnerShadow * drag.pressProgress,
                alpha = drag.pressProgress,
            )
        },
        layerBlock = {
            val velocity = drag.velocity / GlassTokens.STRETCH_VELOCITY
            scaleX = drag.scaleX / (1f - stretch(velocity * GlassTokens.STRETCH_X))
            scaleY = drag.scaleY * (1f - stretch(velocity * GlassTokens.STRETCH_Y))
        },
        // stays tinted while pressed, only lighter, so the glass reads as glass over a dark page too
        onDrawSurface = {
            drawRect(restColor, alpha = lerp(1f, GlassTokens.PILL_PRESSED_TINT, drag.pressProgress))
            drawRect(Color.White.copy(alpha = GlassTokens.PILL_PRESSED_SHINE * drag.pressProgress))
        },
    )

private fun stretch(amount: Float): Float = amount.coerceIn(-GlassTokens.STRETCH_LIMIT, GlassTokens.STRETCH_LIMIT)
