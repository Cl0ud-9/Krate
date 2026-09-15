package dev.cl0ud9.manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private const val DARK_ALPHA = 0.5f
private const val LIGHT_ALPHA = 0.2f
private const val DARK_LUMINANCE_THRESHOLD = 0.5f

// a tint wash behind a screen's header, fading to nothing by mid-screen - dark and light themes
// need different bases to read as the same weight: a light, saturated container color at higher
// alpha for dark backgrounds, a darker "on" color at low alpha for light ones
@Composable
fun rememberHeroGradient(): Brush {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val isDark = MaterialTheme.colorScheme.surface.luminance() < DARK_LUMINANCE_THRESHOLD
    return remember(primaryContainer, onPrimaryContainer, isDark) {
        val base =
            if (isDark) {
                primaryContainer.copy(
                    alpha = DARK_ALPHA,
                )
            } else {
                onPrimaryContainer.copy(alpha = LIGHT_ALPHA)
            }
        Brush.verticalGradient(listOf(base, Color.Transparent))
    }
}
