package dev.cl0ud9.krate.ui.details

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.navigation.LocalLiquidGlass
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.krateGlow
import dev.cl0ud9.krate.ui.theme.rememberHeroGradient
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLine

// the turning shape behind the logo, the logo, and how the page arrives
private val ShapeSize = 148.dp
private val LogoSize = 56.dp
private val TextWidth = 320.dp
private const val TURN_MS = 24_000
private const val FULL_TURN = 360f

// Home's glow is tuned for a whole header; on a shape this small it needs twice the colour to read the same
private const val GLOW_STRENGTH = 2.2f
private const val ENTRANCE_FROM = 0.6f
private const val ENTRANCE_DAMPING = 0.55f
private const val ENTRANCE_STIFFNESS = 260f

// a page for an app Krate no longer has, instead of loading forever: still and centred, nothing to scroll, with the
// Krate mark in front of a slowly turning shape
@Composable
internal fun GoneApp() {
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val entrance = remember { Animatable(ENTRANCE_FROM) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(ENTRANCE_DAMPING, ENTRANCE_STIFFNESS)) }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = entrance.value
                    scaleY = entrance.value
                    alpha = ((entrance.value - ENTRANCE_FROM) / (1f - ENTRANCE_FROM)).coerceIn(0f, 1f)
                },
        ) { GoneIllustration() }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = rememberKrateLine(Moment.TRACK_GONE),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = TextWidth),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "This app was taken off your list. Anything you installed from it is still on your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = TextWidth),
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(onClick = { back?.onBackPressed() }) { Text("Go back") }
    }
}

// the Krate mark as Home shows it, still, in front of a slowly turning shape filled the way Home's header is: its
// two-colour glow with liquid glass on, with the glass rim too, and its colour wash with glass off
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoneIllustration() {
    val glass = LocalLiquidGlass.current
    // read while drawing, so the page itself never recomposes as it turns
    val turn =
        rememberInfiniteTransition(label = "gone")
            .animateFloat(0f, FULL_TURN, infiniteRepeatable(tween(TURN_MS, easing = LinearEasing)), label = "turn")
    val cookie = MaterialShapes.Cookie12Sided.toShape()
    val wash = rememberHeroGradient()
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ShapeSize)) {
        Box(
            modifier =
                Modifier
                    .size(ShapeSize)
                    .graphicsLayer { rotationZ = turn.value }
                    .glassRim(cookie)
                    .clip(cookie)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .then(if (glass) Modifier.krateGlow(GLOW_STRENGTH) else Modifier.background(wash)),
        )
        Icon(
            painter = painterResource(R.drawable.ic_krate),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(LogoSize),
        )
    }
}
