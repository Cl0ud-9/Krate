package dev.cl0ud9.krate.ui.navigation

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.ui.components.LocalIntroHeaderSlot
import dev.cl0ud9.krate.ui.theme.KrateHeroTitle
import dev.cl0ud9.krate.ui.util.isShortScreen
import dev.cl0ud9.krate.voice.KrateVoice
import dev.cl0ud9.krate.voice.Moment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val HeaderStartInset = 24.dp

// the Home mark's hidden trick: this many taps, each within the gap of the last, spin it and show a secret line
private const val SECRET_TAPS = 5
private const val SECRET_TAP_GAP_MS = 600L
private const val SECRET_SHOWN_MS = 4_000L
private const val SPIN_DEGREES = 360f

// a tab's header: big title (led by the Krate mark on Home), its actions, and a one-line subtitle, all on one left edge
@Composable
internal fun TabHeader(
    title: String,
    icon: Painter?,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit,
) {
    // a phone on its side keeps the header tight and drops the subtitle, leaving the height to the content
    val short = isShortScreen()
    // the line the mark's secret shows in place of the subtitle for a moment
    var secret by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(secret) {
        if (secret != null) {
            delay(SECRET_SHOWN_MS)
            secret = null
        }
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = if (short) 4.dp else 20.dp, bottom = if (short) 10.dp else 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = HeaderStartInset, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabTitle(
                title = title,
                icon = icon,
                onSecret = { secret = KrateVoice.pick(Moment.SECRET) },
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
        if (subtitle != null && !short) {
            Text(
                text = secret?.takeIf { KrateVoice.playful } ?: subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = HeaderStartInset, end = 24.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun TabTitle(
    title: String,
    icon: Painter?,
    onSecret: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val introSlot = LocalIntroHeaderSlot.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            val spin = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            val haptics = LocalHapticFeedback.current
            var taps by remember { mutableIntStateOf(0) }
            var lastTap by remember { mutableLongStateOf(0L) }
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .then(if (introSlot != null) Modifier.introSlot(introSlot) else Modifier)
                        .padding(end = 10.dp)
                        .size(34.dp)
                        .graphicsLayer { rotationZ = spin.value }
                        // no ripple or sound: it's a secret, not a button
                        .clickable(interactionSource = null, indication = null) {
                            val now = SystemClock.uptimeMillis()
                            taps = if (now - lastTap < SECRET_TAP_GAP_MS) taps + 1 else 1
                            lastTap = now
                            if (taps >= SECRET_TAPS) {
                                taps = 0
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                onSecret()
                                scope.launch {
                                    spin.snapTo(0f)
                                    spin.animateTo(SPIN_DEGREES, spring(dampingRatio = 0.55f, stiffness = 120f))
                                    spin.snapTo(0f)
                                }
                            }
                        },
            )
        }
        Text(
            text = title,
            style = KrateHeroTitle,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// during the intro the header mark is hidden and its space opens up, so the flying mark lands in a gap made for it
private fun Modifier.introSlot(openness: () -> Float): Modifier =
    graphicsLayer { alpha = 0f }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val width = (placeable.width * openness()).roundToInt()
            layout(width, placeable.height) { placeable.placeRelative(0, 0) }
        }
