package dev.cl0ud9.krate.ui.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import dev.cl0ud9.krate.ui.navigation.LocalLiquidGlass
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val PRESSED_SCALE = 0.97f
private const val GLASS_DAMPING = 0.5f
private const val GLASS_STIFFNESS = 300f

// slight scale-down on press, a common expressive touch on clickable surfaces. A modifier node rather than a composed
// modifier: a list row pays nothing extra to compose, and a press only updates the row's layer, never recomposes it
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier = this then PressScaleElement(interactionSource)

private data class PressScaleElement(
    val interactionSource: InteractionSource,
) : ModifierNodeElement<PressScaleNode>() {
    override fun create(): PressScaleNode = PressScaleNode(interactionSource)

    override fun update(node: PressScaleNode) = node.watch(interactionSource)

    override fun InspectorInfo.inspectableProperties() {
        name = "pressScale"
        properties["interactionSource"] = interactionSource
    }
}

private class PressScaleNode(
    private var interactionSource: InteractionSource,
) : Modifier.Node(),
    LayoutModifierNode,
    CompositionLocalConsumerModifierNode {
    private val scale = Animatable(1f)
    private var watching: Job? = null

    // read while drawing, so each step of the spring only redraws the layer
    private val layerBlock: GraphicsLayerScope.() -> Unit = {
        val value = scale.value
        scaleX = value
        scaleY = value
    }

    override fun onAttach() = startWatching()

    override fun onDetach() {
        watching = null
    }

    fun watch(source: InteractionSource) {
        if (source == interactionSource) return
        interactionSource = source
        watching?.cancel()
        if (isAttached) startWatching()
    }

    private fun startWatching() {
        watching =
            coroutineScope.launch {
                val presses = mutableListOf<PressInteraction.Press>()
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> presses += interaction
                        is PressInteraction.Release -> presses -= interaction.press
                        is PressInteraction.Cancel -> presses -= interaction.press
                    }
                    settle(pressed = presses.isNotEmpty())
                }
            }
    }

    private fun settle(pressed: Boolean) {
        val target = if (pressed) PRESSED_SCALE else 1f
        if (scale.targetValue == target) return
        // with glass on it springs back with a little give, like the glass does
        val spec =
            if (currentValueOf(LocalLiquidGlass)) {
                spring<Float>(dampingRatio = GLASS_DAMPING, stiffness = GLASS_STIFFNESS)
            } else {
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
            }
        coroutineScope.launch { scale.animateTo(target, spec) }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layerBlock) }
    }
}
