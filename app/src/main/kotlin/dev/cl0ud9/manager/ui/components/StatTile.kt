package dev.cl0ud9.manager.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.pressScale
import dev.cl0ud9.manager.ui.util.rememberDebouncedOnClick

private const val VALUE_FADE_MS = 200
private const val LABEL_ALPHA = 0.72f

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
            val debouncedClick = rememberDebouncedOnClick(onClick = onClick)
            Modifier
                .pressScale(interactionSource)
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
        modifier = modifier.fillMaxWidth().then(tapModifier),
        shape = ShapeCache.smooth20,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = contentColor,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AnimatedContent(
                targetState = value,
                label = "stat-value",
                transitionSpec = {
                    fadeIn(animationSpec = tween(VALUE_FADE_MS)) togetherWith
                        fadeOut(animationSpec = tween(VALUE_FADE_MS))
                },
            ) { animatedValue ->
                Text(
                    text = animatedValue,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = LABEL_ALPHA),
            )
        }
    }
}
