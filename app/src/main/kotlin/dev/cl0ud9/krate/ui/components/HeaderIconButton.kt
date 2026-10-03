package dev.cl0ud9.krate.ui.components

import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.cl0ud9.krate.ui.navigation.LocalLiquidGlass

private const val DARK_LUMINANCE = 0.5f

// the frosted fill, see-through enough to show the glow behind it
private const val FROST_DARK = 0.12f
private const val FROST_LIGHT = 0.55f

// a small round button on a header (What's new, Settings, Back): solid normally; with liquid glass on, a frosted,
// see-through circle that shows the glowing header behind it
@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    colors: HeaderButtonColors,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val glass = LocalLiquidGlass.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < DARK_LUMINANCE
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor =
                    if (glass) Color.White.copy(alpha = if (dark) FROST_DARK else FROST_LIGHT) else colors.solid,
                contentColor = if (glass) colors.glassContent else colors.content,
            ),
        content = icon,
    )
}
