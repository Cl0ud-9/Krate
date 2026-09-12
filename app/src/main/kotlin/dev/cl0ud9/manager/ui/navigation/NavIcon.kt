package dev.cl0ud9.manager.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
// sharper terminals than the rounded look this app wants - matching that exactly needs the
// Material Symbols Rounded asset family itself, bundled as drawables
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
    tint: Color = Color.Unspecified,
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
