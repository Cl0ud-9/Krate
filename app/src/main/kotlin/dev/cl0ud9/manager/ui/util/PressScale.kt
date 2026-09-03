package dev.cl0ud9.manager.ui.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

private const val PRESSED_SCALE = 0.97f

// slight scale-down on press, a common expressive touch on clickable surfaces
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier =
    composed {
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) PRESSED_SCALE else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
            label = "pressScale",
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }
