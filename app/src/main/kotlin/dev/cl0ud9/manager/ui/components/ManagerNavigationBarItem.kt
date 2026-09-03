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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val IndicatorWidth = 64.dp
private val IndicatorHeight = 32.dp
private val IndicatorInset = 4.dp
private val IconWidth = 48.dp
private val IconHeight = 24.dp
private val IndicatorShape = RoundedCornerShape(16.dp)
private const val ICON_SCALE_SELECTED = 1.1f
private const val FAST_FADE_MS = 120
private const val COLOR_FADE_MS = 150

// pill indicator + icon bounce + label fade
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
    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = tween(COLOR_FADE_MS),
        label = "navItemColor",
    )

    Column(
        modifier =
            modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavIconWithIndicator(selected = selected, icon = icon, label = label, tint = iconColor)

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(color = iconColor),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun NavIconWithIndicator(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    tint: Color,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) ICON_SCALE_SELECTED else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navIconScale",
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(IndicatorWidth, IndicatorHeight)) {
        AnimatedVisibility(
            visible = selected,
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
                        .padding(horizontal = IndicatorInset)
                        .background(color = MaterialTheme.colorScheme.secondaryContainer, shape = IndicatorShape),
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(IconWidth, IconHeight)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
        ) {
            CompositionLocalProvider(LocalContentColor provides tint) {
                Icon(imageVector = icon, contentDescription = label)
            }
        }
    }
}
