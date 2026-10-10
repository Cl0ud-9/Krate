package dev.cl0ud9.krate.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.pressScale
import dev.cl0ud9.krate.ui.util.rememberDebouncedOnClick

private const val VALUE_FADE_MS = 200
private const val VALUE_ROLL_MS = 320
private const val LABEL_ALPHA = 0.72f

// the smallest a label shrinks to before it fits a narrow tile at a big font
private val MIN_LABEL_SIZE = 10.sp

// label: sentence case, no trailing colon. value: large semibold figure, crossfades when it changes.
// onClick is optional - not every stat this tile shows has somewhere useful to navigate to. Fixed
// to surfaceContainer/onSurface (no color params) - every caller here wants the same tone, and a
// customizable pair would just be unused surface area given detekt's parameter-count limit
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tapModifier =
        if (onClick != null) {
            val haptics = LocalHapticFeedback.current
            val debouncedClick =
                rememberDebouncedOnClick {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                }
            Modifier
                .pressScale(interactionSource)
                .clip(ShapeCache.rounded20)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = debouncedClick,
                )
        } else {
            Modifier
        }
    val contentColor = MaterialTheme.colorScheme.onSurface
    Card(
        modifier = modifier.fillMaxWidth().glassRim(ShapeCache.rounded20).then(tapModifier),
        shape = ShapeCache.rounded20,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = contentColor,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StatValue(value)
            val style = MaterialTheme.typography.bodyMedium
            BasicText(
                text = label,
                style = style.copy(color = contentColor.copy(alpha = LABEL_ALPHA)),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = style.fontSize),
            )
        }
    }
}

// the figure itself: a count rolls up as it grows and down as it shrinks, like an odometer; anything else crossfades
@Composable
private fun StatValue(value: String) {
    AnimatedContent(
        targetState = value,
        label = "stat-value",
        modifier = Modifier.clipToBounds(),
        transitionSpec = {
            val from = initialState.toIntOrNull()
            val to = targetState.toIntOrNull()
            if (from == null || to == null || from == to) {
                fadeIn(tween(VALUE_FADE_MS)) togetherWith fadeOut(tween(VALUE_FADE_MS))
            } else {
                val up = if (to > from) 1 else -1
                val enter = slideInVertically(tween(VALUE_ROLL_MS)) { it * up } + fadeIn(tween(VALUE_ROLL_MS))
                val exit = slideOutVertically(tween(VALUE_ROLL_MS)) { -it * up } + fadeOut(tween(VALUE_ROLL_MS))
                enter togetherWith exit
            }
        },
    ) { animatedValue ->
        Text(
            text = animatedValue,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
