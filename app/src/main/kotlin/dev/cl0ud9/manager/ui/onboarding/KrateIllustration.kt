@file:Suppress("MagicNumber")

package dev.cl0ud9.manager.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.ui.components.SineWaveLine
import dev.cl0ud9.manager.ui.theme.ShapeCache

private const val BOB_MS = 2600
private const val BOB_PX = 9f
private val SCENE_HEIGHT = 290.dp
private val CRATE_WIDTH = 236.dp
private val CRATE_HEIGHT = 112.dp
private val CRATE_BOTTOM = 22.dp

// a tile's color pair and silhouette
private enum class TileStyle(
    val square: Boolean,
) {
    SECONDARY_SQUARE(true),
    TERTIARY_SQUARE(true),
    PRIMARY_ROUND(false),
    SECONDARY_ROUND(false),
    TERTIARY_ROUND(false),
}

// one app tile coming out of the crate; center is measured from the scene's top middle
private class FloatingTile(
    @param:DrawableRes val icon: Int,
    val size: Dp,
    val center: DpOffset,
    val rotation: Float,
    val phase: Float,
    val style: TileStyle,
)

// the three tiles rising out of the open top, half still inside the crate
private val RISING =
    listOf(
        FloatingTile(
            R.drawable.ic_nav_apps_filled,
            66.dp,
            DpOffset((-76).dp, 132.dp),
            -14f,
            0.2f,
            TileStyle.SECONDARY_SQUARE,
        ),
        FloatingTile(
            R.drawable.ic_nav_update_filled,
            62.dp,
            DpOffset(78.dp, 136.dp),
            16f,
            0.7f,
            TileStyle.TERTIARY_SQUARE,
        ),
        FloatingTile(R.drawable.ic_krate, 88.dp, DpOffset(0.dp, 118.dp), 5f, 0.45f, TileStyle.PRIMARY_ROUND),
    )

// two small ones already floating free above it
private val FLOATING =
    listOf(
        FloatingTile(R.drawable.ic_stat_krate, 46.dp, DpOffset((-118).dp, 52.dp), -10f, 0f, TileStyle.TERTIARY_ROUND),
        FloatingTile(
            R.drawable.ic_check_rounded,
            40.dp,
            DpOffset(112.dp, 64.dp),
            12f,
            0.55f,
            TileStyle.SECONDARY_ROUND,
        ),
    )

// the welcome art: app tiles rising out of an open crate on a moving waterline, all in theme colors
@Composable
internal fun KrateIllustration(modifier: Modifier = Modifier) {
    val bob by rememberInfiniteTransition(label = "tiles").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BOB_MS), RepeatMode.Reverse),
        label = "bob",
    )
    Box(modifier = modifier.fillMaxWidth().height(SCENE_HEIGHT)) {
        CrateLid()
        RISING.forEach { Tile(it, bob) }
        CrateFront()
        FLOATING.forEach { Tile(it, bob) }
        Waterline()
    }
}

@Composable
private fun BoxScope.Tile(
    tile: FloatingTile,
    bob: Float,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) =
        when (tile.style) {
            TileStyle.SECONDARY_SQUARE, TileStyle.SECONDARY_ROUND ->
                colors.secondaryContainer to
                    colors.onSecondaryContainer
            TileStyle.PRIMARY_ROUND -> colors.primary to colors.onPrimary
            TileStyle.TERTIARY_SQUARE, TileStyle.TERTIARY_ROUND ->
                colors.tertiaryContainer to
                    colors.onTertiaryContainer
        }
    Surface(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .offset(x = tile.center.x, y = tile.center.y - tile.size / 2)
                .graphicsLayer { translationY = (((bob + tile.phase) % 1f) - 0.5f) * 2f * BOB_PX }
                .rotate(tile.rotation)
                .size(tile.size),
        shape = if (tile.style.square) ShapeCache.smooth20 else ShapeCache.smoothPill,
        color = container,
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(tile.icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(tile.size * 0.48f),
            )
        }
    }
}

private fun BoxScope.crateArea(): Modifier =
    Modifier.align(Alignment.BottomCenter).padding(bottom = CRATE_BOTTOM).size(CRATE_WIDTH, CRATE_HEIGHT)

// the lid, tipped open behind the crate on its back edge
@Composable
private fun BoxScope.CrateLid() {
    val lid = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier = crateArea()) {
        val path =
            Path().apply {
                moveTo(size.width * 0.02f, size.height * 0.02f)
                lineTo(size.width * 0.86f, -size.height * 0.46f)
                lineTo(size.width * 0.93f, -size.height * 0.3f)
                lineTo(size.width * 0.1f, size.height * 0.16f)
                close()
            }
        drawPath(path, lid)
    }
}

// the crate's front: a rounded body with a rim, two slats and a plank at each end
@Composable
private fun BoxScope.CrateFront() {
    val body = MaterialTheme.colorScheme.primaryContainer
    val detail = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.28f)
    val rim = MaterialTheme.colorScheme.primary
    Canvas(modifier = crateArea()) {
        drawRoundRect(color = body, size = size, cornerRadius = CornerRadius(20.dp.toPx()))
        drawRoundRect(color = rim, size = Size(size.width, 12.dp.toPx()), cornerRadius = CornerRadius(6.dp.toPx()))
        val slat = 6.dp.toPx()
        val inset = 20.dp.toPx()
        listOf(0.45f, 0.75f).forEach { fraction ->
            val y = size.height * fraction
            drawLine(detail, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = slat)
        }
        listOf(inset, size.width - inset).forEach { x ->
            drawLine(detail, Offset(x, 22.dp.toPx()), Offset(x, size.height - 14.dp.toPx()), strokeWidth = slat)
        }
    }
}

@Composable
private fun BoxScope.Waterline() {
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(22.dp)) { drawRect(color = surface) }
    WaveStroke(color = surface, width = 16.dp)
    WaveStroke(color = MaterialTheme.colorScheme.primary, width = 4.dp)
}

@Composable
private fun BoxScope.WaveStroke(
    color: Color,
    width: Dp,
) {
    SineWaveLine(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 8.dp)
                .padding(bottom = 4.dp),
        animate = true,
        color = color,
        alpha = 0.95f,
        strokeWidth = width,
        amplitude = 4.dp,
        waves = 7.6f,
    )
}
