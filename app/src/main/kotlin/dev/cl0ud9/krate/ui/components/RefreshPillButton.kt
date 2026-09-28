package dev.cl0ud9.krate.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.theme.ShapeCache
import kotlin.math.ceil

private const val SPIN_DURATION_MS = 900
private const val FULL_TURN_DEGREES = 360f
private const val SETTLE_MS = 350
private val BUTTON_HEIGHT = 42.dp
private val ICON_SIZE = 20.dp

// the pill-button row shape from the Apps header - a real FilledTonalButton (elevation, ripple,
// disabled state all come from the component itself) tinted tertiary, not secondary, and a solid
// 42dp height rather than padding-driven sizing. Spins continuously while refreshing instead of
// swapping to a separate spinner, so the button itself is the busy indicator
@Composable
fun RefreshPillButton(
    isRefreshing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // spins while refreshing, then eases on to the next full turn instead of snapping back mid-spin
    val spin = remember { Animatable(0f) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            while (true) spin.animateTo(spin.value + FULL_TURN_DEGREES, tween(SPIN_DURATION_MS, easing = LinearEasing))
        } else if (spin.value % FULL_TURN_DEGREES != 0f) {
            val nextTurn = ceil(spin.value / FULL_TURN_DEGREES) * FULL_TURN_DEGREES
            spin.animateTo(nextTurn, tween(SETTLE_MS, easing = FastOutSlowInEasing))
            spin.snapTo(0f)
        }
    }

    FilledTonalButton(
        // a tap mid-refresh is ignored rather than disabling the pill, which would flash it grey
        onClick = { if (!isRefreshing) onClick() },
        modifier = modifier.height(BUTTON_HEIGHT),
        shape = ShapeCache.pill,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh_rounded),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE).graphicsLayer { rotationZ = spin.value },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Refresh", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}
