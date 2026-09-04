package dev.cl0ud9.manager.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.ui.theme.ShapeCache

private const val VALUE_FADE_MS = 200
private const val LABEL_ALPHA = 0.72f

// label: sentence case, no trailing colon. value: large semibold figure, crossfades when it changes
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth20,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
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
