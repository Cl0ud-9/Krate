package dev.cl0ud9.krate.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.ui.components.AppIconAvatar

private const val FLIGHT_DAMPING = 0.82f
private const val FLIGHT_STIFFNESS = 380f

// the trip back from an app's page to the row it was opened from. Shared elements can't follow a back swipe: they
// run on their own clock, so the icon would race ahead of the finger. Instead the icon stays in the page's card while
// the page is dragged, and flies home only once the page is really going, from wherever the card has got to
internal object BackFlight {
    // the trip the open page would make going back: the row's key, the app, and the page's own icon to start from
    var armed by mutableStateOf<Armed?>(null)

    // an icon on its way home right now
    var flying by mutableStateOf<Flying?>(null)

    // where each row's icon is, read every frame of a flight so it lands where the row really is
    val rows = mutableMapOf<String, LayoutCoordinates>()

    class Armed(
        val key: String,
        val app: AppProfile,
    ) {
        var from: LayoutCoordinates? = null
    }

    class Flying(
        val key: String,
        val app: AppProfile,
        val from: Rect,
    )

    // the page is going for good: the icon leaves the card from wherever it is now
    fun launch(key: String) {
        val armed = armed?.takeIf { it.key == key } ?: return
        val from =
            armed.from
                ?.takeIf { it.isAttached }
                ?.boundsInWindow()
                ?.takeIf { it.width > 0f }
        this.armed = null
        if (from != null && flying == null) flying = Flying(key, armed.app, from)
    }
}

// drawn over every page, under nothing: the icon flying home, passing under a tab's header as the list does
@Composable
internal fun BackFlightOverlay() {
    val flying = BackFlight.flying ?: return
    val progress = remember(flying) { Animatable(0f) }
    LaunchedEffect(flying) {
        progress.animateTo(1f, spring(FLIGHT_DAMPING, FLIGHT_STIFFNESS))
        BackFlight.flying = null
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInWindow() }
                .drawWithContent {
                    clipRect(top = (IconFlight.panelTop - origin.y).coerceAtLeast(0f)) {
                        this@drawWithContent.drawContent()
                    }
                },
    ) {
        Box(
            modifier =
                Modifier.graphicsLayer {
                    // the row's live position each frame, so it lands where the row is even while its page settles
                    val target =
                        BackFlight.rows[flying.key]?.takeIf { it.isAttached }?.boundsInWindow() ?: flying.from
                    val now = lerp(flying.from, target, progress.value)
                    val natural = size.width.takeIf { it > 0f } ?: 1f
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = now.width / natural
                    scaleY = scaleX
                    translationX = now.left - origin.x
                    translationY = now.top - origin.y
                },
        ) { AppIconAvatar(app = flying.app, size = PageIconSize) }
    }
}
