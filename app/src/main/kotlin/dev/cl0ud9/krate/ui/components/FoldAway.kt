package dev.cl0ud9.krate.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val FADE_OUT_MS = 160
private const val FADE_IN_MS = 220
private const val FOLD_MS = 260

// a card that goes away in two beats: it fades, then the room it took closes up smoothly, the gap after it included.
// The room is closed by the container's own size animation, which the scrolling page sees frame by frame, so a page
// scrolled to its end doesn't jump when something above gives up its space. [gap] is the space the card leaves
// below it, kept inside so there is none left when it's gone
@Composable
fun FoldAway(
    visible: Boolean,
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    animateIn: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.animateContentSize(tween(FOLD_MS))) {
        AnimatedVisibility(
            visible = visible,
            enter = if (animateIn) fadeIn(tween(FADE_IN_MS)) else EnterTransition.None,
            exit = fadeOut(tween(FADE_OUT_MS)),
        ) {
            Column {
                content()
                if (gap > 0.dp) Spacer(modifier = Modifier.height(gap))
            }
        }
    }
}
