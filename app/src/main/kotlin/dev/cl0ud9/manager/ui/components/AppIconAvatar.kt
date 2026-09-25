package dev.cl0ud9.manager.ui.components

import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.cl0ud9.manager.domain.model.AppProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

// shows the app's real launcher icon when it's installed on this device, else the icon the catalog
// ships for it (rendered from its APK), and only falls back to a lettered avatar when neither exists
@Composable
fun AppIconAvatar(
    app: AppProfile,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_AVATAR_SIZE,
) {
    val displayName = app.displayName
    val seed = app.id
    val packageName = app.packageName
    val catalogIconPng = app.iconPng
    val context = LocalContext.current
    val realIcon by
        produceState<ImageBitmap?>(initialValue = null, packageName, catalogIconPng) {
            value =
                withContext(Dispatchers.IO) {
                    val installed =
                        runCatching { context.packageManager.getApplicationIcon(packageName) }
                            .getOrNull()
                            ?.let(Drawable::toBitmap)
                            ?.asImageBitmap()
                    installed ?: catalogIconPng?.let(::decodeCatalogIcon)
                }
        }

    val icon = realIcon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape),
        )
        return
    }

    val color = remember(seed) { AvatarPalette[seed.hashCode().mod(AvatarPalette.size)] }
    // every palette swatch is dark/saturated enough for white today, but derive it from luminance
    // rather than hardcoding white so this stays correct if the palette or a contrast mode changes it
    val labelColor =
        remember(color) {
            if (color.luminance() > CONTRAST_LUMINANCE_THRESHOLD) Color.Black else Color.White
        }
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
            color = labelColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private const val CONTRAST_LUMINANCE_THRESHOLD = 0.5f

private fun decodeCatalogIcon(base64Png: String): ImageBitmap? =
    runCatching {
        val bytes = Base64.decode(base64Png, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
