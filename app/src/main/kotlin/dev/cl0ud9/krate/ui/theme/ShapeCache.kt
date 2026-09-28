package dev.cl0ud9.krate.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// the app's corner sizes, built once and shared, so no screen allocates a new shape per composition
object ShapeCache {
    val rounded4: Shape = RoundedCornerShape(4.dp)
    val rounded8: Shape = RoundedCornerShape(8.dp)
    val rounded10: Shape = RoundedCornerShape(10.dp)
    val rounded12: Shape = RoundedCornerShape(12.dp)
    val rounded14: Shape = RoundedCornerShape(14.dp)
    val rounded16: Shape = RoundedCornerShape(16.dp)
    val rounded20: Shape = RoundedCornerShape(20.dp)
    val rounded24: Shape = RoundedCornerShape(24.dp)
    val rounded28: Shape = RoundedCornerShape(28.dp)
    val rounded32: Shape = RoundedCornerShape(32.dp)
    val pill: Shape = RoundedCornerShape(50.dp)

    // for a radius that isn't one of the fixed sizes above (the nav bar's user-adjustable corner radius), remembered
    // per radius so a recomposition from an unrelated animation reuses it
    @Composable
    fun corner(radius: Dp): Shape = remember(radius) { RoundedCornerShape(radius) }

    // the content panel sitting under a screen's header strip - rounded only at the top, square at the bottom since it
    // runs all the way to the edge of the screen behind the floating nav bar
    @Composable
    fun contentPanel(radius: Dp): Shape =
        remember(
            radius,
        ) { RoundedCornerShape(topStart = radius, topEnd = radius, bottomStart = 0.dp, bottomEnd = 0.dp) }

    // the same sizes for MaterialExpressiveTheme's own components
    val materialShapes: Shapes =
        Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        )
}
