package dev.cl0ud9.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// how much of a tab's bottom edge the nav bar covers (the bar plus the system bar under it); tab content runs behind it
val LocalNavBarClearance = compositionLocalOf { 0.dp }

// how far the fade reaches above the bar, so the list dims into it instead of meeting a hard edge
private val FADE_ABOVE_BAR = 56.dp
private const val FADE_CLEAR_UNTIL = 0.2f
private const val FADE_SOLID_FROM = 0.8f

// the fade a tab's content scrolls into behind the floating nav bar
@Composable
fun BoxScope.NavBarFade() {
    val clearance: Dp = LocalNavBarClearance.current
    if (clearance <= 0.dp) return
    val solid = MaterialTheme.colorScheme.surfaceContainerLowest
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(clearance + FADE_ABOVE_BAR)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        FADE_CLEAR_UNTIL to Color.Transparent,
                        FADE_SOLID_FROM to solid,
                        1f to solid,
                    ),
                ),
    )
}
