package dev.cl0ud9.manager.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

private const val FADE_OUT_MS = 150
private const val FADE_IN_MS = 250

// the old content fades out before the new one fades in, so two labels never overlap mid-swap
fun <S> fadeThrough(): AnimatedContentTransitionScope<S>.() -> ContentTransform =
    {
        (fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS)) togetherWith fadeOut(tween(FADE_OUT_MS)))
            .using(
                SizeTransform(clip = false) { _, _ -> tween(FADE_OUT_MS + FADE_IN_MS, easing = FastOutSlowInEasing) },
            )
    }

private data class ButtonLabel(
    val busy: Boolean,
    val text: String,
    @DrawableRes val icon: Int?,
)

// a button's icon and label that turns into a small spinner and busyText while its action runs
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BusyButtonContent(
    busy: Boolean,
    text: String,
    @DrawableRes icon: Int? = null,
    busyText: String = "Checking...",
) {
    AnimatedContent(
        targetState = ButtonLabel(busy, if (busy) busyText else text, icon),
        transitionSpec = fadeThrough(),
        label = "busy-button",
    ) { label ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                label.busy -> LoadingIndicator(modifier = Modifier.size(20.dp), color = LocalContentColor.current)
                label.icon != null ->
                    Icon(painterResource(label.icon), contentDescription = null, modifier = Modifier.size(18.dp))
            }
            if (label.busy || label.icon != null) Spacer(modifier = Modifier.width(8.dp))
            Text(label.text)
        }
    }
}
