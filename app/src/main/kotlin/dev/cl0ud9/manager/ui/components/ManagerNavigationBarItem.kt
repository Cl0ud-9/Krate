package dev.cl0ud9.manager.ui.components

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cl0ud9.manager.ui.navigation.NavIcon

// the selection pill only ever bounds the icon; the label sits below it, unpilled, animating its own color/weight
private val IndicatorWidth = 64.dp
private val IndicatorHeight = 32.dp
private val IconWidth = 48.dp
private val IconHeight = 24.dp
private val IndicatorShape = RoundedCornerShape(16.dp)
private val IconShape = RoundedCornerShape(12.dp)
private const val COLOR_FADE_MS = 150
private const val PILL_ENTER_FADE_MS = 100
private const val PILL_EXIT_MS = 100
private const val ICON_SCALE_SELECTED = 1.1f

@Suppress("LongParameterList")
@Composable
fun RowScope.ManagerNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: NavIcon,
    label: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer
    val selectedTextColor = MaterialTheme.colorScheme.onSurface
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedIconColor else unselectedColor,
        animationSpec = tween(COLOR_FADE_MS),
        label = "iconColor",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) selectedTextColor else unselectedColor,
        animationSpec = tween(COLOR_FADE_MS),
        label = "textColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) ICON_SCALE_SELECTED else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "iconScale",
    )

    // no ripple - the pill fade+scale-in and icon bounce already carry the press/selection feedback
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier =
            modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = null,
                ).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NavItemIcon(selected = selected, icon = icon, iconColor = iconColor, iconScale = iconScale)
        if (!compact) {
            Spacer(modifier = Modifier.height(4.dp))
            NavItemLabel(label = label, textColor = textColor, selected = selected)
        }
    }
}

// icon + its indicator pill, boxed to a fixed size so the pill never grows to match the label below it
@Composable
private fun NavItemIcon(
    selected: Boolean,
    icon: NavIcon,
    iconColor: Color,
    iconScale: Float,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(IndicatorWidth, IndicatorHeight),
    ) {
        NavItemPill(selected = selected)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(IconWidth, IconHeight)
                    .clip(IconShape)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
        ) {
            CompositionLocalProvider(LocalContentColor provides iconColor) {
                NavIcon(icon = icon, contentDescription = null, size = IconHeight)
            }
        }
    }
}

@Composable
private fun BoxScope.NavItemPill(selected: Boolean) {
    androidx.compose.animation.AnimatedVisibility(
        visible = selected,
        enter =
            fadeIn(animationSpec = tween(PILL_ENTER_FADE_MS)) +
                scaleIn(
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                ),
        exit = fadeOut(animationSpec = tween(PILL_EXIT_MS)) + scaleOut(animationSpec = tween(PILL_EXIT_MS)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = IndicatorShape,
                    ),
        )
    }
}

// always shown - color/weight just animate with selection, like the icon does
@Composable
private fun NavItemLabel(
    label: String,
    textColor: Color,
    selected: Boolean,
) {
    Box(modifier = Modifier.padding(top = 4.dp)) {
        ProvideTextStyle(
            value =
                MaterialTheme.typography.labelMedium.copy(
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                ),
        ) {
            Text(label)
        }
    }
}
