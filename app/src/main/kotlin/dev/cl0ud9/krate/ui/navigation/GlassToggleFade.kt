package dev.cl0ud9.krate.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

// switching liquid glass on or off changes how the whole app is drawn, so instead of snapping it takes one picture of
// the screen just before the switch and fades it out over the new look. Nothing runs until the switch is flipped
private val HoleMargin = 2.dp

internal object GlassToggleFade {
    var snapshot by mutableStateOf<ImageBitmap?>(null)

    // the switch that was flipped: it slides to its new place live, instead of fading out of its old one
    var hole: Rect? = null
}

// on the switch itself, so the fading picture leaves it out
internal fun Modifier.keptOutOfGlassFade(): Modifier = onGloballyPositioned { GlassToggleFade.hole = it.boundsInRoot() }

// the Appearance switch: snapshots the screen, then applies the change underneath the fading picture
@Composable
internal fun rememberGlassToggle(apply: (Boolean) -> Unit): (Boolean) -> Unit {
    val view = LocalView.current
    return remember(view, apply) {
        { enabled ->
            val window = view.context.findActivity()?.window
            if (window == null || view.width == 0 || view.height == 0) {
                apply(enabled)
            } else {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                // PixelCopy reads what's on screen, blur and all, which drawing the views again can't
                PixelCopy.request(window, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) GlassToggleFade.snapshot = bitmap.asImageBitmap()
                    apply(enabled)
                }, Handler(Looper.getMainLooper()))
            }
        }
    }
}

// the old look on top, fading away; drawn over everything while it lasts
@Composable
internal fun GlassToggleOverlay() {
    val shot = GlassToggleFade.snapshot ?: return
    val alpha = remember(shot) { Animatable(1f) }
    LaunchedEffect(shot) {
        alpha.animateTo(0f, tween(GlassTokens.TOGGLE_FADE_MS))
        GlassToggleFade.snapshot = null
    }
    Image(
        bitmap = shot,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier =
            Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.drawWithContent {
                // switch-shaped and a hair past its edge, so no box shows where the row's colour changes underneath
                val hole = GlassToggleFade.hole?.inflate(HoleMargin.toPx())
                if (hole == null) {
                    drawContent()
                } else {
                    val pill = Path().apply { addRoundRect(RoundRect(hole, CornerRadius(hole.height / 2))) }
                    clipPath(pill, ClipOp.Difference) { this@drawWithContent.drawContent() }
                }
            },
    )
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
