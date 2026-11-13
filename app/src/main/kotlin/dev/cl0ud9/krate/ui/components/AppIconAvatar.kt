package dev.cl0ud9.krate.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.packageinfo.PackageChanges
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
    val icon = rememberAppIcon(app)
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

// the app's real launcher icon when it's installed, else the catalog's; null while loading or when there's neither
@Composable
internal fun rememberAppIcon(app: AppProfile): ImageBitmap? {
    val packageName = app.packageName
    val catalogIconPng = app.iconPng
    val context = LocalContext.current
    // read again when any app is installed, replaced or removed, so an updated app shows its icon at once
    val changes = PackageChanges.version.intValue
    val side = with(LocalDensity.current) { ICON_DP.dp.roundToPx() }
    // the size is part of the key, so a larger display size never reuses an icon read for a smaller one
    val cacheKey = packageName + ":" + catalogIconPng.hashCode() + ":" + side
    // the last icon seen shows at once, so a list scrolled back or a screen returned to never flashes letters first
    val icon by
        produceState(initialValue = AppIconCache.get(cacheKey)?.icon, packageName, catalogIconPng, changes) {
            // one already read since the last package change is as current as a new read would be
            AppIconCache.get(cacheKey)?.takeIf { it.changes == changes }?.let { return@produceState }
            val loaded =
                withContext(Dispatchers.IO) {
                    val installed =
                        runCatching { context.packageManager.getApplicationIcon(packageName) }
                            .getOrNull()
                            ?.let { it.toBitmap(side, side) }
                            ?.asImageBitmap()
                    installed ?: catalogIconPng?.let(::decodeCatalogIcon)
                }
            if (loaded != null) AppIconCache.put(cacheKey, loaded, changes)
            value = loaded
        }
    return icon
}

private const val CONTRAST_LUMINANCE_THRESHOLD = 0.5f

// the largest an icon is drawn on any screen, so large launcher artwork isn't held at full size
private const val ICON_DP = 64

// about two dozen icons in memory at most, counted by their size rather than by how many
private const val ICON_CACHE_BYTES = 6 * 1024 * 1024

// icons already loaded this run, and the package-change count each was read at
private object AppIconCache {
    class Entry(
        val icon: ImageBitmap,
        val changes: Int,
    )

    private val icons =
        object : LruCache<String, Entry>(ICON_CACHE_BYTES) {
            override fun sizeOf(
                key: String,
                value: Entry,
            ): Int = value.icon.width * value.icon.height * BYTES_PER_PIXEL
        }

    fun get(key: String): Entry? = icons.get(key)

    fun put(
        key: String,
        icon: ImageBitmap,
        changes: Int,
    ) {
        icons.put(key, Entry(icon, changes))
    }
}

private const val BYTES_PER_PIXEL = 4

private fun decodeCatalogIcon(base64Png: String): ImageBitmap? =
    runCatching {
        val bytes = Base64.decode(base64Png, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
