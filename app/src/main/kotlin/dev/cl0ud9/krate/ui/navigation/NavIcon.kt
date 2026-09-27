package dev.cl0ud9.krate.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

// Home/Apps/Updates/Settings all render through Material Symbols Rounded vector drawables
// (res/drawable/ic_nav_*.xml, downloaded from Google's public, Apache-2.0 material-design-icons
// repo - see licenses/material-symbols-NOTICE.txt) rather than Compose's bundled Icons.Filled/
// Icons.Outlined family. Compose's own "Rounded" icon set is missing plain Home and Settings
// glyphs entirely (a known gap in its auto-generated subset), and Icons.Filled has noticeably
// sharper terminals than the rounded look wanted here - matching that needs the Material Symbols
// Rounded asset family itself, bundled as drawables
sealed interface NavIcon {
    data class Vector(
        val imageVector: ImageVector,
    ) : NavIcon

    data class Drawable(
        @param:DrawableRes val resId: Int,
    ) : NavIcon
}

@Composable
fun NavIcon(
    icon: NavIcon,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    // the drawables' own fillColor is a placeholder white so they show up in a plain XML preview -
    // defaulting to the current content color (not Unspecified) is what actually makes them follow
    // the caller's tint instead of always rendering that placeholder white regardless of theme
    tint: Color = LocalContentColor.current,
) {
    when (icon) {
        is NavIcon.Vector ->
            Icon(
                imageVector = icon.imageVector,
                contentDescription = contentDescription,
                modifier = modifier.size(size),
                tint = tint,
            )

        is NavIcon.Drawable ->
            Icon(
                painter = painterResource(icon.resId),
                contentDescription = contentDescription,
                modifier = modifier.size(size),
                tint = tint,
            )
    }
}
