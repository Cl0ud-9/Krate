package dev.cl0ud9.manager.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.ui.theme.ShapeCache

private const val SPIN_DURATION_MS = 900

// a manual, always-visible companion to pull-to-refresh - a pill button that
// is an actual refresh action. Spins continuously while refreshing
// instead of swapping to a separate spinner, so the button itself is the busy indicator
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

    Surface(
        onClick = onClick,
        enabled = !isRefreshing,
        modifier = modifier,
        shape = ShapeCache.smoothPill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE).rotate(if (isRefreshing) spinAngle else 0f),
            )
            Text(text = "Refresh", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

private const val FULL_TURN_DEGREES = 360f
private val ICON_SIZE = 18.dp
