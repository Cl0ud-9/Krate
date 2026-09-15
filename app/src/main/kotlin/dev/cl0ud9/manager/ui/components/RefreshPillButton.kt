package dev.cl0ud9.manager.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.ui.theme.ShapeCache

private const val SPIN_DURATION_MS = 900
private const val FULL_TURN_DEGREES = 360f
private val BUTTON_HEIGHT = 42.dp
private val ICON_SIZE = 20.dp

// the pill-button row shape from the Apps header - a real FilledTonalButton (elevation, ripple,
// disabled state all come from the component itself) tinted tertiary, not secondary, and a solid
// 42dp height rather than padding-driven sizing. Spins continuously while refreshing instead of
// swapping to a separate spinner, so the button itself is the busy indicator
@Composable
fun RefreshPillButton(
    isRefreshing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refreshPillSpin")
    val spinAngle by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = FULL_TURN_DEGREES,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(SPIN_DURATION_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "refreshPillSpinAngle",
        )

    FilledTonalButton(
        onClick = onClick,
        enabled = !isRefreshing,
        modifier = modifier.height(BUTTON_HEIGHT),
        shape = ShapeCache.smoothPill,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh_rounded),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE).rotate(if (isRefreshing) spinAngle else 0f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Refresh", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}
