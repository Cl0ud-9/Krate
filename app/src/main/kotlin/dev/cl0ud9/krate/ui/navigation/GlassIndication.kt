package dev.cl0ud9.krate.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import dev.cl0ud9.krate.ui.components.LocalNavBarClearance
import kotlinx.coroutines.launch
import kotlin.math.max

private const val HALF = 0.5f
private const val GLOW_DARK = 0.18f
private const val GLOW_LIGHT = 0.14f
private const val FOCUS_ALPHA = 0.1f
private const val GLOW_SPREAD = 1.2f
private const val GLOW_REACH = 0.45f
private const val WASH = 0.5f

// a quick tap still shows the glow: it lights at least this far before fading
private const val TAP_FLOOR = 0.6f
private const val TAP_LIGHT_MS = 80

// with glass on, touching a card or row lights it under the finger, the way Krate's glass answers a touch, instead of
// Material's ripple: a soft glow that springs in where the finger lands and fades as it lifts, clipped to the card
// just as the ripple was. A focused or hovered card keeps a faint wash, so keyboards and switch access still see it
@Composable
internal fun rememberPressIndication(glass: Boolean): Indication {
    val ripple = LocalIndication.current
    if (!glass) return ripple
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF
    // white light adds to a dark card; a light card takes a faint wash of the theme's colour instead
    val glow =
        if (dark) {
            Color.White.copy(
                alpha = GLOW_DARK,
            )
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = GLOW_LIGHT)
        }
    val blend = if (dark) BlendMode.Plus else BlendMode.SrcOver
    return remember(glow, blend) { GlassIndication(glow, blend) }
}

// what every screen under the bar reads: the room the bar takes, whether glass is on, and the press look that goes
// with it
@Composable
internal fun ProvideNavLocals(
    navBarClearance: Dp,
    glass: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalNavBarClearance provides navBarClearance,
        LocalLiquidGlass provides glass,
        LocalIndication provides rememberPressIndication(glass),
        content = content,
    )
}

// a tappable card or row with the app's press look, glass or ripple, clipped to its shape. For Material's own clickable
// Surface, which always ripples
@Composable
fun Modifier.pressable(
    shape: Shape,
    onClick: () -> Unit,
): Modifier =
    clip(shape).clickable(
        interactionSource = null,
        indication = LocalIndication.current,
        role = Role.Button,
        onClick = onClick,
    )

private class GlassIndication(
    private val glow: Color,
    private val blend: BlendMode,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        GlassIndicationNode(interactionSource, glow, blend)

    override fun equals(other: Any?): Boolean = other is GlassIndication && other.glow == glow && other.blend == blend

    override fun hashCode(): Int = 31 * glow.hashCode() + blend.hashCode()
}

private class GlassIndicationNode(
    private val interactionSource: InteractionSource,
    private val glow: Color,
    private val blend: BlendMode,
) : Modifier.Node(),
    DrawModifierNode {
    private val press = Animatable(0f)
    private val pressSpec = spring(0.5f, 300f, 0.001f)
    private var point = Offset.Unspecified
    private var focused = false
    private var hovered = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        point = interaction.pressPosition
                        launch { press.animateTo(1f, pressSpec) }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel ->
                        launch {
                            if (press.value < TAP_FLOOR) press.animateTo(1f, tween(TAP_LIGHT_MS))
                            press.animateTo(0f, pressSpec)
                        }
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    is HoverInteraction.Enter -> hovered = true
                    is HoverInteraction.Exit -> hovered = false
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (focused || hovered) drawRect(glow.copy(alpha = FOCUS_ALPHA))
        val lit = press.value
        if (lit > 0f) {
            // the whole piece lights a little, so the press reads as its own rounded shape; a short wide row would
            // otherwise show the glow cut off flat at its top and bottom
            drawRect(glow.copy(alpha = glow.alpha * WASH * lit), blendMode = blend)
            val center = if (point.isSpecified) point else Offset(size.width / 2, size.height / 2)
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = glow.alpha * lit), Color.Transparent),
                        center = center,
                        // wide enough to fade out on its own along a long row
                        radius = max(size.minDimension * GLOW_SPREAD, size.maxDimension * GLOW_REACH),
                    ),
                blendMode = blend,
            )
        }
    }
}
