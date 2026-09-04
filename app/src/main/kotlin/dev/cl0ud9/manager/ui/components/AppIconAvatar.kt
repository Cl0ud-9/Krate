package dev.cl0ud9.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DEFAULT_AVATAR_SIZE = 44.dp

// a fixed, saturated palette for per-app identity/recognizability, independent of the dynamic theme -
// the same pattern Gmail/Contacts use for avatars while the surrounding chrome stays Material You
private val AvatarPalette =
    listOf(
        Color(0xFFE53935),
        Color(0xFFFB8C00),
        Color(0xFF43A047),
        Color(0xFF00897B),
        Color(0xFF1E88E5),
        Color(0xFF3949AB),
        Color(0xFF8E24AA),
        Color(0xFFD81B60),
    )

@Composable
fun AppIconAvatar(
    displayName: String,
    seed: String,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_AVATAR_SIZE,
) {
    val color = remember(seed) { AvatarPalette[seed.hashCode().mod(AvatarPalette.size)] }
    val initial =
        remember(displayName) {
            displayName
                .trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString() ?: "?"
        }

    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
