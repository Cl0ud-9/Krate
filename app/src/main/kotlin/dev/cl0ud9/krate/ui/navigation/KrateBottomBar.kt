package dev.cl0ud9.krate.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.cl0ud9.krate.domain.model.NavBarStyle
import dev.cl0ud9.krate.ui.components.KrateNavigationBarItem

@Composable
internal fun KrateBottomBar(
    navController: NavHostController,
    currentRoute: String?,
    appearance: NavBarAppearance,
    visibilityProgress: Float,
    barHeightPx: MutableIntState,
) {
    val geometry = rememberNavBarGeometry(appearance)
    val full = appearance.style == NavBarStyle.FULL_WIDTH
    val shape = navBarShape(geometry.topRadius, geometry.bottomRadius)
    val glass = appearance.glass
    val destinations = KrateBottomNavDestinations
    val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val (motion, measureTabs) =
        rememberGlassTabsMotion(selectedIndex, destinations.size) { index ->
            navController.navigateToTab(destinations[index].route)
        }
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
                .padding(horizontal = geometry.sideMargin)
                .liquidGlass(
                    glass,
                    shape,
                    edge = if (full) GlassEdge.TOP else GlassEdge.ALL,
                    cornerRadius = geometry.topRadius,
                    layerBlock = if (glass != null) motion.barLayer else null,
                    press = { motion.highlight.progress },
                ).then(if (glass != null) motion.highlight.modifier else Modifier),
        shape = shape,
        // Material's own navigation bar color: a tone above the surface panel behind it, plus the shadow; glass draws
        // its own surface and shadow instead
        color = if (appearance.glass != null) Color.Transparent else NavigationBarDefaults.containerColor,
        shadowElevation = if (appearance.glass != null) 0.dp else 3.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (full) Modifier.windowInsetsPadding(SideInsets) else Modifier)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = geometry.innerBottomPadding)
                    .then(if (glass != null) measureTabs.then(motion.gestures) else Modifier),
        ) {
            if (glass != null) GlassTabPill(motion, glass, appearance.compactMode)
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                destinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    KrateNavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(destination.route) },
                        icon = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        label = stringResource(destination.labelRes),
                        compact = appearance.compactMode,
                        pill = glass == null,
                    )
                }
            }
        }
    }
}

// over a gesture handle the floating bar sits as far from the screen's bottom and sides as the handle is tall, so a
// matching corner radius lines up with the phone's own screen corners; above 3-button nav it keeps a gap of its own
// instead of resting on the buttons. Full width runs edge to edge over the system bar
internal val NavBarContentHeight = 90.dp
internal val NavBarCompactContentHeight = 64.dp
private val FLOATING_GAP = 14.dp
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

// the strip the bar takes up at the bottom of the screen: its content plus everything below it
@Composable
internal fun navBarFootprint(
    compact: Boolean,
    floating: Boolean,
): Dp {
    val content = if (compact) NavBarCompactContentHeight else NavBarContentHeight
    val below = if (floating) floatingNavBarMargins().second else WindowInsets.navigationBars.bottomDp()
    return content + below
}

// the gaps beside and below the floating bar: a gesture handle's own height, or over 3-button nav (and in landscape,
// where its buttons move to the side and leave no bottom bar) a fixed gap clear of the buttons and the screen edge
@Composable
internal fun floatingNavBarMargins(): Pair<Dp, Dp> {
    val inset = WindowInsets.navigationBars.bottomDp()
    return if (inset > 0.dp && inset <= WIDE_INSET) inset to inset else FLOATING_GAP to inset + FLOATING_GAP
}

@Composable
private fun WindowInsets.bottomDp(): Dp = asPaddingValues().calculateBottomPadding()

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
    return remember(topDp, bottomDp) {
        RoundedCornerShape(topStart = topDp.dp, topEnd = topDp.dp, bottomStart = bottomDp.dp, bottomEnd = bottomDp.dp)
    }
}
