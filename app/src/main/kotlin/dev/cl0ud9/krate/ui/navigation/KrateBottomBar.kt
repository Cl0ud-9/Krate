package dev.cl0ud9.krate.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.cl0ud9.krate.domain.model.NavBarStyle
import dev.cl0ud9.krate.ui.components.KrateNavigationBarItem
import dev.cl0ud9.krate.ui.theme.LocalUseSmoothCorners
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
internal fun KrateBottomBar(
    navController: NavHostController,
    currentRoute: String?,
    appearance: NavBarAppearance,
    visibilityProgress: Float,
    barHeightPx: MutableIntState,
) {
    val geometry = rememberNavBarGeometry(appearance)
    val compact = appearance.compactMode
    val full = appearance.style == NavBarStyle.FULL_WIDTH
    Surface(
        modifier =
            Modifier
                // measured with its margins, so the content above can leave room for all of it
                .onSizeChanged { barHeightPx.intValue = it.height }
                .graphicsLayer { translationY = barHeightPx.intValue * (1f - visibilityProgress) }
                .fillMaxWidth()
                .padding(bottom = geometry.bottomMargin)
                .height(geometry.height)
                .then(if (full) Modifier else Modifier.windowInsetsPadding(SideInsets))
                .padding(horizontal = geometry.sideMargin),
        shape = navBarShape(geometry.topRadius, geometry.bottomRadius),
        // surfaceContainerHighest keeps the bar distinct from the surface-toned panel behind it in both themes
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (full) Modifier.windowInsetsPadding(SideInsets) else Modifier)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = geometry.innerBottomPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KrateBottomNavDestinations.forEach { destination ->
                val selected = currentRoute == destination.route
                KrateNavigationBarItem(
                    selected = selected,
                    onClick = { navController.navigateToTab(destination.route) },
                    icon = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    label = stringResource(destination.labelRes),
                    compact = compact,
                )
            }
        }
    }
}

// the floating bar sits as far from the screen's bottom and sides as the system bar is tall, so a matching
// corner radius lines up with the phone's own screen corners; full width runs edge to edge over the system bar
internal val NavBarContentHeight = 90.dp
internal val NavBarCompactContentHeight = 64.dp
private val MAX_SIDE_MARGIN = 14.dp
private val WIDE_INSET = 30.dp
private const val NAV_BAR_MORPH_MS = 400

// where the bar sits and how it's rounded, every value eased so switching style or radius glides
private class NavBarGeometry(
    val sideMargin: Dp,
    val bottomMargin: Dp,
    val height: Dp,
    val innerBottomPadding: Dp,
    val topRadius: Dp,
    val bottomRadius: Dp,
)

// the strip the bar takes up at the bottom of the screen: its content plus the system bar, the same in both styles
@Composable
internal fun navBarFootprint(compact: Boolean): Dp =
    (if (compact) NavBarCompactContentHeight else NavBarContentHeight) +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

// the gap below and beside the floating bar, the same as the system navigation bar's height (capped for 3-button bars)
@Composable
internal fun floatingNavBarMargins(): Pair<Dp, Dp> {
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return (if (inset > WIDE_INSET) MAX_SIDE_MARGIN else inset) to inset
}

@Composable
private fun rememberNavBarGeometry(appearance: NavBarAppearance): NavBarGeometry {
    val full = appearance.style == NavBarStyle.FULL_WIDTH
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val (side, bottom) = floatingNavBarMargins()
    val content = if (appearance.compactMode) NavBarCompactContentHeight else NavBarContentHeight
    val spec = tween<Dp>(NAV_BAR_MORPH_MS)
    val sideMargin by animateDpAsState(if (full) 0.dp else side, spec, label = "navSide")
    val bottomMargin by animateDpAsState(if (full) 0.dp else bottom, spec, label = "navBottom")
    val height by animateDpAsState(if (full) content + inset else content, spec, label = "navHeight")
    val innerBottom by animateDpAsState(if (full) inset else 0.dp, spec, label = "navInner")
    val radius by animateDpAsState(appearance.cornerRadius.dp, spec, label = "navRadius")
    val bottomRadius by animateDpAsState(
        if (full) 0.dp else appearance.cornerRadius.dp,
        spec,
        label = "navBottomRadius",
    )
    return NavBarGeometry(sideMargin, bottomMargin, height, innerBottom, radius, bottomRadius)
}

// rounded to whole dp so an animating radius reuses shapes instead of building a new one every frame
@Composable
private fun navBarShape(
    top: Dp,
    bottom: Dp,
): Shape {
    val topDp = top.value.toInt()
    val bottomDp = bottom.value.toInt()
    val smooth = LocalUseSmoothCorners.current
    return remember(topDp, bottomDp, smooth) {
        if (smooth) {
            AbsoluteSmoothCornerShape(
                cornerRadiusTL = topDp.dp,
                smoothnessAsPercentTL = SMOOTHNESS,
                cornerRadiusTR = topDp.dp,
                smoothnessAsPercentTR = SMOOTHNESS,
                cornerRadiusBL = bottomDp.dp,
                smoothnessAsPercentBL = SMOOTHNESS,
                cornerRadiusBR = bottomDp.dp,
                smoothnessAsPercentBR = SMOOTHNESS,
            )
        } else {
            RoundedCornerShape(
                topStart = topDp.dp,
                topEnd = topDp.dp,
                bottomStart = bottomDp.dp,
                bottomEnd = bottomDp.dp,
            )
        }
    }
}

private const val SMOOTHNESS = 60
