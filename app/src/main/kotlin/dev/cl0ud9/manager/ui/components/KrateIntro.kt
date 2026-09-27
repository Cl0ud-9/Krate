package dev.cl0ud9.manager.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.voice.Moment
import dev.cl0ud9.manager.voice.rememberKrateLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// true while the intro covers the screen; Home holds its dialogs back until the Krate mark has landed
val LocalIntroPlaying = compositionLocalOf { false }

// while the intro runs, how far Home's header has opened the space its own mark will fill (0 to 1); null otherwise
val LocalIntroHeaderSlot = compositionLocalOf<(() -> Float)?> { null }

// the mark at centre stage, and where Home's header mark sits: 34dp, 24dp in, 20dp under the status bar
private val STAGE_SIZE = 176.dp
private val HEADER_ICON_SIZE = 34.dp
private val HEADER_ICON_CENTER_X = 41.dp
private val HEADER_ICON_CENTER_Y = 48.dp
private val TEXT_GAP = 28.dp
private val TEXT_RISE = 16.dp
private const val STAGE_CENTER_Y = 0.4f
private const val START_TILT = 15f

// slow on purpose: a one-time welcome, not something to hurry past
private const val COME_FORTH_MS = 800
private const val COVER_MS = 450
private const val TEXT_IN_MS = 550
private const val TEXT_HOLD_MS = 1900L
private const val TEXT_OUT_MS = 400
private const val PAUSE_MS = 150L
private const val FLY_MS = 950
private const val RELEASE_MS = 250L
private const val WARM_UP_FRAMES = 3
private const val BACKDROP_FADE_START = 0.3f

// a hair short of opaque: fully opaque, the GPU skips drawing Home beneath and pays its first draw mid-flight
private const val BACKDROP_MAX_ALPHA = 254f / 255f
private const val CONTAINER_FADE_END = 0.6f
private const val GLYPH_START_FRACTION = 0.5f
private const val GLYPH_STAGE_FRACTION = 0.56f
private const val SLOT_OPEN_START = 0.5f
private const val SLOT_OPEN_SPAN = 0.45f

// the animated values behind the intro, each running 0 to 1; owned by the caller so Home's header can follow the flight
class KrateIntroProgress {
    internal val comeForth = Animatable(0f)
    internal val cover = Animatable(0f)
    internal val text = Animatable(0f)
    internal val fly = Animatable(0f)

    // the header makes room while the mark is on its way, finishing just before it arrives
    fun headerSlot(): Float =
        FastOutSlowInEasing.transform(((fly.value - SLOT_OPEN_START) / SLOT_OPEN_SPAN).coerceIn(0f, 1f))
}

// once after setup: the setup tile's mark comes forward, says hello, then flies into Home's header as Home appears
@Composable
fun KrateIntro(
    start: Rect?,
    progress: KrateIntroProgress,
    onCovered: () -> Unit,
    onLanded: () -> Unit,
    onFinished: () -> Unit,
) {
    val callbacks = rememberUpdatedState(Triple(onCovered, onLanded, onFinished))
    LaunchedEffect(Unit) {
        val (covered, landed, finished) = callbacks.value
        // the overlay's costly first frames pass while it still looks exactly like setup, so the motion starts smooth
        repeat(WARM_UP_FRAMES) { withFrameNanos {} }
        launch { progress.comeForth.animateTo(1f, tween(COME_FORTH_MS, easing = FastOutSlowInEasing)) }
        progress.cover.animateTo(1f, tween(COVER_MS, easing = LinearOutSlowInEasing))
        delay((COME_FORTH_MS - COVER_MS).toLong())
        progress.text.animateTo(1f, tween(TEXT_IN_MS, easing = LinearOutSlowInEasing))
        // Home is heavy to build, so it's built now, while nothing on screen is moving, not mid-animation
        covered()
        delay(TEXT_HOLD_MS)
        progress.text.animateTo(0f, tween(TEXT_OUT_MS, easing = FastOutSlowInEasing))
        delay(PAUSE_MS)
        progress.fly.animateTo(1f, tween(FLY_MS, easing = FastOutSlowInEasing))
        landed()
        // any dialog Home opens now sits above this in its own window; the overlay just keeps taps off Home meanwhile
        delay(RELEASE_MS)
        finished()
    }
    // nothing underneath reacts while the intro plays, taps and Back included
    BackHandler {}
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
    ) {
        IntroBackdrop(progress)
        IntroMark(start = start, progress = progress)
        IntroGreeting(progress = progress)
    }
}

// covers setup as the mark comes forward, then lifts off Home during the flight
@Composable
private fun IntroBackdrop(progress: KrateIntroProgress) {
    val color = MaterialTheme.colorScheme.surface
    // faded as it's drawn, not through a layer, which would repaint a whole screen offscreen every frame
    Box(
        modifier =
            Modifier.fillMaxSize().drawBehind {
                val lift = ((progress.fly.value - BACKDROP_FADE_START) / (1f - BACKDROP_FADE_START)).coerceIn(0f, 1f)
                val shown = progress.cover.value * (1f - lift) * BACKDROP_MAX_ALPHA
                if (shown > 0f) drawRect(color, alpha = shown)
            },
    )
}

// the mark: from the setup tile (tilted, muted) to centre stage (upright, primary), then down to the header;
// each frame it's only re-placed and redrawn, never recomposed
@Composable
private fun IntroMark(
    start: Rect?,
    progress: KrateIntroProgress,
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val glyph = painterResource(R.drawable.ic_krate)
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val stagePx = with(density) { STAGE_SIZE.roundToPx() }
    // Home's header keeps clear of the side insets (landscape camera cutout), so its mark sits that much further in
    val sideInset =
        WindowInsets.safeDrawing
            .only(
                WindowInsetsSides.Horizontal,
            ).getLeft(density, LocalLayoutDirection.current)
    val headerCenter =
        with(density) { Offset(sideInset + HEADER_ICON_CENTER_X.toPx(), statusBarTop + HEADER_ICON_CENTER_Y.toPx()) }
    val headerScale = HEADER_ICON_SIZE / STAGE_SIZE
    // without a measured tile the mark simply grows out of the centre of the stage
    val startScale = start?.let { it.width / stagePx } ?: 0f
    Box(
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(stagePx, stagePx))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        // the tile was measured against the window; this overlay may sit inset (landscape cutout)
                        val origin = coordinates?.positionInRoot() ?: Offset.Zero
                        val come = progress.comeForth.value
                        val fly = progress.fly.value
                        val stageCenter = Offset(constraints.maxWidth / 2f, constraints.maxHeight * STAGE_CENTER_Y)
                        val center =
                            if (fly > 0f) {
                                lerp(stageCenter, headerCenter, fly)
                            } else {
                                lerp(start?.center?.minus(origin) ?: stageCenter, stageCenter, come)
                            }
                        placeable.place((center.x - stagePx / 2f).roundToInt(), (center.y - stagePx / 2f).roundToInt())
                    }
                }.graphicsLayer {
                    val come = progress.comeForth.value
                    val fly = progress.fly.value
                    val scale = if (fly > 0f) lerp(1f, headerScale, fly) else lerp(startScale, 1f, come)
                    scaleX = scale
                    scaleY = scale
                    rotationZ = lerp(START_TILT, 0f, come)
                    alpha = if (start == null) come.coerceIn(0f, 1f) else 1f
                }.drawBehind {
                    val come = progress.comeForth.value
                    val fly = progress.fly.value
                    val containerAlpha = 1f - (fly / CONTAINER_FADE_END).coerceIn(0f, 1f)
                    if (containerAlpha > 0f) {
                        drawCircle(
                            lerp(colors.surfaceContainerHigh, colors.primaryContainer, come),
                            alpha = containerAlpha,
                        )
                    }
                    val tint = lerp(lerp(colors.onSurfaceVariant, colors.onPrimaryContainer, come), colors.primary, fly)
                    // drawn at full size and scaled, so the vector's cached raster is reused every frame
                    scale(glyphFraction(come, fly)) {
                        with(glyph) { draw(size, colorFilter = ColorFilter.tint(tint)) }
                    }
                },
    )
}

// "Welcome to Krate" and a line under the mark, rising in and fading out again before the flight;
// built with the overlay, so its text is ready long before it shows
@Composable
private fun IntroGreeting(progress: KrateIntroProgress) {
    val line = rememberKrateLine(Moment.WELCOME)
    val density = LocalDensity.current
    Column(
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    val top = constraints.maxHeight * STAGE_CENTER_Y + (STAGE_SIZE / 2 + TEXT_GAP).toPx()
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, top.roundToInt()) }
                }.fillMaxWidth()
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    val shown = progress.text.value
                    // the two lines never overlap, so each fades on its own without an offscreen pass
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = shown
                    translationY = with(density) { TEXT_RISE.toPx() } * (1f - shown)
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Welcome to Krate",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = line,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// the glyph's share of the mark: the setup tile's proportion, a touch bolder on stage, filling it once in the header
private fun glyphFraction(
    come: Float,
    fly: Float,
): Float = if (fly > 0f) lerp(GLYPH_STAGE_FRACTION, 1f, fly) else lerp(GLYPH_START_FRACTION, GLYPH_STAGE_FRACTION, come)
