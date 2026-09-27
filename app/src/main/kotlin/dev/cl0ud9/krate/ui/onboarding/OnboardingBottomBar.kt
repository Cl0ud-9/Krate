@file:Suppress("MagicNumber")

package dev.cl0ud9.krate.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.navigation.SideInsets
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.isShortScreen

private const val MORPH_MS = 600
private val SHEET_RADIUS = 36.dp

// a step change waits this long before anything moves, so the next page's first, costly frame lands before motion
internal const val STEP_CHANGE_DELAY_MS = 64
private const val ROTATE_MS = 900
private const val LABEL_MS = 300
private const val FULL_TURN = 360f
private const val SHAPE_CYCLE = 3

// circle, rounded square, then a leaf (top start and bottom end round) - the button cycles through these
@Suppress("MagicNumber")
private fun cornersFor(step: Int): List<Float> =
    when (step % SHAPE_CYCLE) {
        0 -> listOf(50f, 50f, 50f, 50f)
        1 -> listOf(26f, 26f, 26f, 26f)
        else -> listOf(50f, 22f, 50f, 22f)
    }

// the setup sheet at the bottom: where you are on the left, the way forward on the right
@Composable
internal fun OnboardingBottomBar(
    step: Int,
    lastStep: Int,
    canContinue: Boolean,
    onContinue: () -> Unit,
) {
    // slimmer on a phone on its side, where height is scarce
    val short = isShortScreen()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.contentPanel(SHEET_RADIUS),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth().navigationBarsPadding().windowInsetsPadding(SideInsets).padding(
                    start = 28.dp,
                    end = 12.dp,
                    top = if (short) 8.dp else 12.dp,
                    bottom = if (short) 8.dp else 14.dp,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepLabel(step = step, lastStep = lastStep, modifier = Modifier.weight(1f))
            MorphingNextButton(
                step = step,
                finish = step == lastStep,
                enabled = canContinue,
                size = if (short) 64.dp else 80.dp,
                onClick = onContinue,
            )
        }
    }
}

@Composable
private fun StepLabel(
    step: Int,
    lastStep: Int,
    modifier: Modifier,
) {
    AnimatedContent(
        targetState = step,
        modifier = modifier,
        transitionSpec = {
            val forward = targetState > initialState
            val slide = tween<IntOffset>(LABEL_MS, delayMillis = STEP_CHANGE_DELAY_MS)
            val fade = tween<Float>(LABEL_MS, delayMillis = STEP_CHANGE_DELAY_MS)
            (slideInVertically(slide) { if (forward) it else -it } + fadeIn(fade))
                .togetherWith(slideOutVertically(slide) { if (forward) -it else it } + fadeOut(fade))
                .using(SizeTransform(clip = false))
        },
        label = "step-label",
    ) { current ->
        if (current == 0) {
            Text(
                text = "Let's Go!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = "Step $current of $lastStep",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MorphingNextButton(
    step: Int,
    finish: Boolean,
    enabled: Boolean,
    size: Dp,
    onClick: () -> Unit,
) {
    val morph = tween<Float>(MORPH_MS, delayMillis = STEP_CHANGE_DELAY_MS, easing = FastOutSlowInEasing)
    val target = cornersFor(step)
    val topStart by animateFloatAsState(target[0], morph, label = "topStart")
    val topEnd by animateFloatAsState(target[1], morph, label = "topEnd")
    val bottomEnd by animateFloatAsState(target[2], morph, label = "bottomEnd")
    val bottomStart by animateFloatAsState(target[3], morph, label = "bottomStart")
    val rotation by animateFloatAsState(
        step * FULL_TURN,
        tween(ROTATE_MS, delayMillis = STEP_CHANGE_DELAY_MS, easing = FastOutSlowInEasing),
        label = "rotation",
    )
    val shape =
        RoundedCornerShape(
            topStartPercent = topStart.toInt(),
            topEndPercent = topEnd.toInt(),
            bottomEndPercent = bottomEnd.toInt(),
            bottomStartPercent = bottomStart.toInt(),
        )
    val colors = MaterialTheme.colorScheme
    val container = if (enabled) colors.primaryContainer else colors.surfaceContainerHighest
    val content =
        if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
                .copy(
                    alpha = 0.58f,
                )
        }
    Box(
        modifier =
            Modifier
                .size(size)
                .rotate(rotation)
                .clip(shape)
                .background(container)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        NextIcon(finish = finish, rotation = rotation, tint = content)
    }
}

// the arrow, turned into a check on the last step, held upright while the button spins
@Composable
private fun NextIcon(
    finish: Boolean,
    rotation: Float,
    tint: Color,
) {
    AnimatedContent(
        targetState = finish,
        modifier = Modifier.rotate(-rotation),
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(220, delayMillis = 90), initialScale = 0.9f))
                .togetherWith(fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.9f))
        },
        label = "next-icon",
    ) { isFinish ->
        Icon(
            painter =
                painterResource(
                    if (isFinish) R.drawable.ic_check_rounded else R.drawable.ic_arrow_forward_rounded,
                ),
            contentDescription = if (isFinish) "Finish" else "Next",
            tint = tint,
            modifier = Modifier.size(28.dp),
        )
    }
}
