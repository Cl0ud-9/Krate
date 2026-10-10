package dev.cl0ud9.krate.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp

// a floating glass piece answering a touch: it springs a little bigger with a soft light under the finger, and its
// glass blurs and bends a touch more. It only watches the touch, so taps still reach the piece
internal class GlassPress(
    private val light: InteractiveHighlight,
    private val pressedScale: Float,
) {
    val progress: Float get() = light.progress

    // for the glass itself, through liquidGlass's layerBlock
    val glassLayer: GraphicsLayerScope.() -> Unit = { swell(pressedScale, light.progress) }

    // on the piece, after its glass: the content grows with the glass, the light sits under the content
    val modifier: Modifier =
        Modifier
            .then(light.gestureModifier)
            .graphicsLayer { swell(pressedScale, light.progress) }
            .then(light.modifier)
}

// wide: a message or a bar, which grows less than a button does
@Composable
internal fun rememberGlassPress(wide: Boolean = false): GlassPress {
    val scope = rememberCoroutineScope()
    return remember(scope, wide) {
        GlassPress(
            InteractiveHighlight(scope),
            if (wide) GlassTokens.PRESS_SCALE_WIDE else GlassTokens.PRESS_SCALE_SMALL,
        )
    }
}

internal fun GraphicsLayerScope.swell(
    pressedScale: Float,
    progress: Float,
) {
    val scale = lerp(1f, pressedScale, progress)
    scaleX = scale
    scaleY = scale
}
