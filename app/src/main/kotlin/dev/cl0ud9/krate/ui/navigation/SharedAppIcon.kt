package dev.cl0ud9.krate.ui.navigation

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.cl0ud9.krate.domain.model.AppProfile

// an app's icon flying from wherever it was tapped into its App Details page, and back there on the way out. Every icon
// that can open a page is tagged with its place; the tap remembers that place, and only the page it opens links to
// it, so an app shown twice on one screen, or a page opened from a notification or a link, never flies the wrong one
internal val LocalSharedTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }
internal val LocalNavAnimation = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

// how this page is moving: coming in (on the way back, for a tab), going out, and whether it's the top page yet
internal val LocalPageTrip = staticCompositionLocalOf<PageTrip?> { null }

internal class PageTrip(
    val entering: State<Boolean>,
    val leaving: State<Boolean>,
    val onTop: State<Boolean>,
)

// the size the app's page shows its icon at, which the flight home starts from
internal val PageIconSize = 56.dp

private const val ICON_DAMPING = 0.82f

// the bar at the top of a page once it has collapsed, under which the page's icon is out of sight
private val CollapsedBarHeight = 64.dp
private const val ICON_STIFFNESS = 380f
private const val BADGE_RETURN_MS = 180
private const val BADGE_RETURN_SCALE = 0.6f

// a tap older than this belongs to a page that never opened, so it isn't carried over to the next one
private const val ORIGIN_TTL_MS = 1_500L

// an icon partly tucked under a tab's header doesn't fly: the flight would start with the hidden part drawn over the
// header for a frame, so the page just slides in and out over the row instead
private const val MIN_SIGHT = 0.9f

// where a tab's list panel starts, and how much of each list icon is showing above it; read while icons fly
internal object IconFlight {
    var panelTop = 0f
    val sight = mutableMapOf<String, Float>()
}

// a flying icon passes under a tab's header like the list does, instead of over it: it rises out from under the header
// on the way to a page and tucks back under it on the way back
private object BelowPanelClip : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path? {
        val top = IconFlight.panelTop
        if (bounds.top >= top) return null
        return Path().apply { addRect(Rect(bounds.left, top, bounds.right, maxOf(top, bounds.bottom))) }
    }
}

internal object SharedIconOrigin {
    private var appId: String? = null
    private var place: String? = null
    private var at = 0L

    fun tapped(
        appId: String,
        place: String,
    ) {
        this.appId = appId
        // partly tucked under the header: no flight either way, the page just slides in and out over it
        this.place = place.takeIf { (IconFlight.sight[iconKey(appId, place)] ?: 1f) >= MIN_SIGHT }
        at = SystemClock.uptimeMillis()
    }

    // the place an App Details page for this app was opened from, once
    fun take(appId: String): String? {
        val fresh = this.appId == appId && SystemClock.uptimeMillis() - at < ORIGIN_TTL_MS
        val taken = place.takeIf { fresh }
        this.appId = null
        place = null
        return taken
    }
}

// a destination's own enter and exit, for the icons inside it to fly with
@Composable
internal fun AnimatedVisibilityScope.WithNavAnimation(
    entry: NavBackStackEntry,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    val scope = this
    val current = navController.currentBackStackEntryAsState()
    val trip =
        remember(entry) {
            fun moving() = scope.transition.currentState != scope.transition.targetState
            PageTrip(
                entering = derivedStateOf { moving() && scope.transition.targetState == EnterExitState.Visible },
                leaving = derivedStateOf { moving() && scope.transition.targetState == EnterExitState.PostExit },
                // a back swipe leaves the page being dragged away on top until the finger lets go
                onTop = derivedStateOf { current.value?.id == entry.id },
            )
        }
    CompositionLocalProvider(LocalNavAnimation provides this, LocalPageTrip provides trip, content = content)
}

private fun iconKey(
    appId: String,
    place: String,
) = "app-icon/$place/$appId"

// on an icon that opens its app's page from [place]. Going in, it flies there as a shared element; coming back, it
// stays hidden while its icon is still in the page, then the page's icon flies home to it once the page is going
@Composable
internal fun Modifier.sharedAppIcon(
    appId: String,
    place: String,
): Modifier {
    val key = iconKey(appId, place)
    val trip = LocalPageTrip.current
    if (trip != null) {
        LaunchedEffect(key) {
            snapshotFlow { trip.entering.value && trip.onTop.value }.collect { home ->
                if (home) BackFlight.launch(key)
            }
        }
    }
    return onGloballyPositioned { coordinates ->
        BackFlight.rows[key] = coordinates
        // window bounds come back already cut by the list panel, so their share of the full height is what shows
        val full = coordinates.size.height
        if (full > 0) IconFlight.sight[key] = coordinates.boundsInWindow().height / full
    }.graphicsLayer {
        val inPage = BackFlight.armed?.key == key && trip?.entering?.value == true
        alpha = if (inPage || BackFlight.flying?.key == key) 0f else 1f
    }.sharedIconFor(key) { trip?.entering?.value != true }
}

// on the icon at the top of App Details; links to wherever the page was opened from, if anywhere
@Composable
internal fun Modifier.sharedDetailsIcon(app: AppProfile): Modifier {
    val place = remember(app.id) { SharedIconOrigin.take(app.id) } ?: return this
    val key = iconKey(app.id, place)
    val trip = LocalPageTrip.current
    // scrolled up under the bar, the icon has nowhere to fly from: going back, it would drop in from the top of the
    // screen. Out of sight it simply waits in its row instead
    var inSight by remember { mutableStateOf(true) }
    val armed = remember(key) { BackFlight.Armed(key, app) }
    DisposableEffect(armed, inSight) {
        if (inSight) {
            BackFlight.armed = armed
        } else if (BackFlight.armed === armed) {
            BackFlight.armed = null
        }
        onDispose { if (BackFlight.armed === armed) BackFlight.armed = null }
    }
    val density = LocalDensity.current
    val barBottom = with(density) { CollapsedBarHeight.toPx() } + WindowInsets.statusBars.getTop(density)
    val watched =
        onGloballyPositioned { coordinates ->
            armed.from = coordinates
            val bounds = coordinates.boundsInWindow()
            val seen = (bounds.top + bounds.bottom) / 2 > barBottom
            if (seen != inSight) inSight = seen
        }.graphicsLayer { alpha = if (BackFlight.flying?.key == key) 0f else 1f }
    return if (inSight) watched.sharedIconFor(key) { trip?.leaving?.value != true } else watched
}

// a shared element only on the way in: [linked] is false for a trip back, which BackFlight flies instead
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.sharedIconFor(
    key: String,
    linked: () -> Boolean,
): Modifier {
    val shared = LocalSharedTransition.current
    val animation = LocalNavAnimation.current
    if (shared == null || animation == null || !linked()) return this
    return with(shared) {
        this@sharedIconFor.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animation,
            boundsTransform = IconBounds,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit),
            clipInOverlayDuringTransition = BelowPanelClip,
        )
    }
}

// a badge on an app's icon: hidden the moment an icon starts flying and back once it has landed, so a badge never sits
// alone in its row while its icon is still on the way, or under the icon as it passes
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.hiddenWhileIconsFly(): Modifier {
    val flying = LocalSharedTransition.current?.isTransitionActive == true || BackFlight.flying != null
    val shown by animateFloatAsState(
        targetValue = if (flying) 0f else 1f,
        animationSpec = if (flying) snap() else tween(BADGE_RETURN_MS),
        label = "badgeShown",
    )
    return graphicsLayer {
        alpha = shown
        scaleX = BADGE_RETURN_SCALE + (1f - BADGE_RETURN_SCALE) * shown
        scaleY = scaleX
    }
}

// a little give at the end of the flight, like Krate's other springs
@OptIn(ExperimentalSharedTransitionApi::class)
private val IconBounds =
    BoundsTransform {
        _: Rect,
        _: Rect,
        ->
        spring(ICON_DAMPING, ICON_STIFFNESS, Rect.VisibilityThreshold)
    }
