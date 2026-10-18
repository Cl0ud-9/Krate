package dev.cl0ud9.krate.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val SWAP_MS = 320
private const val OUT_MS = 100
private const val IN_MS = 220

// the new content starts to appear just before the old is gone, so a card never stands empty between the two
private const val IN_DELAY_MS = 60

// the one curve a block's height follows when something in it changes or comes and goes, so everything that moves in a
// card lands together and the card grows once, not in steps
val SwapMotion = tween<IntSize>(SWAP_MS, easing = FastOutSlowInEasing)

// the old content fades out, the new fades in, and the room it needs opens on the shared curve meanwhile
fun <S> swapTransition(): AnimatedContentTransitionScope<S>.() -> ContentTransform =
    {
        (fadeIn(tween(IN_MS, delayMillis = IN_DELAY_MS)) togetherWith fadeOut(tween(OUT_MS)))
            .using(SizeTransform(clip = false) { _, _ -> SwapMotion })
    }

// content that changes in place, say a status line or a field that turns into a confirmation: it fades through to the
// new content while the block eases to its new height
@Composable
fun <S> Swap(
    target: S,
    modifier: Modifier = Modifier,
    label: String = "swap",
    content: @Composable (S) -> Unit,
) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = swapTransition(),
        contentAlignment = Alignment.TopStart,
        label = label,
    ) { shown -> content(shown) }
}

// something that appears under what's already there and folds away again. [gap] is the space above it, kept inside so
// that space opens and closes with it instead of sitting empty while it's gone
@Composable
fun Reveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(IN_MS, delayMillis = IN_DELAY_MS)) + expandVertically(SwapMotion),
        exit = fadeOut(tween(OUT_MS)) + shrinkVertically(SwapMotion),
    ) {
        Box(modifier = Modifier.padding(top = gap)) { content() }
    }
}

// Reveal for a value that comes and goes: while it folds away it keeps showing the last one it had, rather than
// emptying out before it has shrunk
@Composable
fun <T : Any> RevealValue(
    value: T?,
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    content: @Composable (T) -> Unit,
) {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    Reveal(visible = value != null, modifier = modifier, gap = gap) { last?.let { content(it) } }
}

private const val ARRIVAL_MS = 380L
private const val ARRIVAL_STEP_MS = 140L

// a page's below-the-fold content waits until the screen has finished arriving, then eases in: composing everything in
// the first frame of a push stretches that frame well past its budget (the push itself is the animation the person is
// watching), and what sits off the bottom isn't missed for a moment. Remembered across rotation and restore, so it
// only waits the first time the page opens
@Composable
fun AfterArrival(
    modifier: Modifier = Modifier,
    // later pieces of one page pass a later step, so they land a frame or more apart instead of all in one frame
    step: Int = 0,
    content: @Composable () -> Unit,
) {
    var arrived by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!arrived) {
            delay(ARRIVAL_MS + step * ARRIVAL_STEP_MS)
            arrived = true
        }
    }
    Reveal(visible = arrived, modifier = modifier, content = content)
}
