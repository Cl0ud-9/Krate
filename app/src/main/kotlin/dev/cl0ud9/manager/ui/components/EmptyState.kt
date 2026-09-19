package dev.cl0ud9.manager.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val ICON_BADGE_SIZE = 72
private const val ICON_SIZE = 32
private const val ENTRANCE_SCALE_BASE = 0.8f
private const val ENTRANCE_SCALE_RANGE = 0.2f

// Painter, not ImageVector - lets every call site pass either a custom rounded drawable
// (painterResource) or a stock Compose icon (rememberVectorPainter) through the same param
@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    // a bouncy scale/fade-in on first appearance instead of a static stack - a plain icon+text
    // block otherwise reads as an unfinished placeholder rather than a deliberate state
    var animateIn by remember { mutableStateOf(false) }
    val entranceProgress by
        animateFloatAsState(
            targetValue = if (animateIn) 1f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "empty-state-entrance",
        )
    LaunchedEffect(Unit) { animateIn = true }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(ICON_BADGE_SIZE.dp)
                    .scale(ENTRANCE_SCALE_BASE + entranceProgress * ENTRANCE_SCALE_RANGE)
                    .alpha(entranceProgress.coerceIn(0f, 1f))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = 20.dp)) { action() }
        }
    }
}
