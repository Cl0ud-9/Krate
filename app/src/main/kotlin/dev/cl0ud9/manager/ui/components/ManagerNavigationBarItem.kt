package dev.cl0ud9.manager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.ui.theme.ShapeCache

private val NAV_ICON_SIZE = 24.dp
private const val ICON_SCALE_SELECTED = 1.1f
private const val FAST_FADE_MS = 120
private const val COLOR_FADE_MS = 150

// the selected indicator now wraps icon AND label together as one capsule, not just the icon with
// a bare label floating below it - so the
// selected tab is a single rounded shape around the whole icon+label group
@Composable
fun RowScope.ManagerNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val selectedColor = MaterialTheme.colorScheme.onSecondaryContainer
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val contentColor by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = tween(COLOR_FADE_MS),
        label = "navItemColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) ICON_SCALE_SELECTED else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navIconScale",
    )

    // no ripple at all, by design - a generic ripple either flashes as an unclipped square across the
    // whole tab (unbounded) or, bounded to the pill, reads as a slow, deliberate scale because its
    // animation duration is fixed regardless of target size. The pill fade+scale-in and icon bounce
    // below already give this item its own tuned press/selection feedback; a ripple on top is redundant
    // at best and fights those animations at worst
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.weight(1f).padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        NavItemPill(selected = selected)
        NavItemContent(
            icon = icon,
            label = label,
            visualState = NavItemVisualState(contentColor, iconScale),
            interactionSource = interactionSource,
            onClick = onClick,
        )
    }
}

// bundles the two animated visual values NavItemContent needs, purely to keep that function's
// parameter list short - not a meaningful domain concept on its own
private data class NavItemVisualState(
    val contentColor: Color,
    val iconScale: Float,
)

// the capsule behind icon+label, fading and springing in/out as selection changes - matchParentSize
// makes it fill exactly whatever size NavItemContent's own padding settles on, so the two never drift
@Composable
private fun BoxScope.NavItemPill(selected: Boolean) {
    AnimatedVisibility(
        visible = selected,
        modifier = Modifier.matchParentSize(),
        enter =
            fadeIn(animationSpec = tween(FAST_FADE_MS)) +
                scaleIn(
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                ),
        exit = fadeOut(animationSpec = tween(FAST_FADE_MS)) + scaleOut(animationSpec = tween(FAST_FADE_MS)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.secondaryContainer, shape = ShapeCache.smooth20),
        )
    }
}

@Composable
private fun NavItemContent(
    icon: ImageVector,
    label: String,
    visualState: NavItemVisualState,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val (contentColor, iconScale) = visualState
    Column(
        modifier =
            Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    role = Role.Tab,
                ).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier =
                    Modifier.size(NAV_ICON_SIZE).graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium.copy(color = contentColor))
    }
}
