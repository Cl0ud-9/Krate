package dev.cl0ud9.manager.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import dev.cl0ud9.manager.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// a detail page's heading lives below the back button and rides up with the content as the user
// scrolls, instead of sitting fixed in a conventional toolbar - the min/max bounds and the snap
// halfway through are the same shape as the app's other scrollable sections, just without a subtitle
private val HeaderMinHeight = 64.dp
private val HeaderMaxHeight = 128.dp

// breathing room between the header and whatever the screen's first card/row is - without it, the
// header's surfaceContainerHigh sits directly against a card's surfaceContainer with no gap, and
// the two tones are close enough to read as a rendering mistake rather than a deliberate layering
val DetailContentTopGap = 12.dp
private const val EXPANDED_TITLE_SCALE = 1.15f
private const val COLLAPSED_TITLE_SCALE = 0.9f
private val ExpandedTitleStartPadding = 20.dp
private val CollapsedTitleStartPadding = 64.dp

// the title sits in a fixed-height box, vertically centered on a Y position that's lerped between
// two exact targets rather than a generic top/bottom alignment bias - expanded, that position sits
// near the header's bottom edge, clear of the back button above it; collapsed, it's pinned to the
// back button's own vertical center (below) so the two align exactly instead of only approximately
private val TitleContainerHeight = 64.dp
private val BackButtonTopPadding = 4.dp
private val BackButtonSize = 40.dp
private val CollapsedTitleCenterY = BackButtonTopPadding + BackButtonSize / 2
private val ExpandedTitleCenterY = HeaderMaxHeight - TitleContainerHeight / 2
private val TitleTransformOrigin = TransformOrigin(0f, 0.5f)

// a detail header's live collapse state - height tracks the user's drag directly (not a fixed-time
// animation) so it always matches finger position, and only snaps to an edge once the drag ends
class CollapsingHeaderState internal constructor(
    private val heightPx: Animatable<Float, *>,
    internal val minHeightPx: Float,
    internal val maxHeightPx: Float,
    private val density: Density,
) {
    val headerHeight: Dp
        get() = with(density) { heightPx.value.toDp() }

    val collapseFraction: Float
        get() = (1f - (heightPx.value - minHeightPx) / (maxHeightPx - minHeightPx)).coerceIn(0f, 1f)

    internal val currentPx: Float get() = heightPx.value

    internal suspend fun snapTo(value: Float) = heightPx.snapTo(value.coerceIn(minHeightPx, maxHeightPx))

    internal suspend fun animateTo(value: Float) =
        heightPx.animateTo(
            value.coerceIn(minHeightPx, maxHeightPx),
            spring(stiffness = Spring.StiffnessMedium),
        )
}

// mirrors the drag-to-collapse behavior used for the app's other scrollable headers: scrolling the
// content first shrinks the header (consuming the drag itself) and only once it bottoms out does the
// content underneath actually start moving, so the transition tracks the finger instead of a canned
// animation. Reading scrollState directly (not a LazyListState) since every detail page here is a
// plain Column, not a list
@Composable
fun rememberCollapsingHeaderState(scrollState: ScrollState): CollapsingHeaderState {
    val density = LocalDensity.current
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minHeightPx = with(density) { (HeaderMinHeight + statusBarInset).toPx() }
    val maxHeightPx = with(density) { (HeaderMaxHeight + statusBarInset).toPx() }
    val heightPx = remember { Animatable(maxHeightPx) }
    val state =
        remember(minHeightPx, maxHeightPx) { CollapsingHeaderState(heightPx, minHeightPx, maxHeightPx, density) }
    val scope = rememberCoroutineScope()

    // once the drag ends, settle fully open or fully closed - reopening only if the content is
    // still scrolled to its very top, matching how a mid-list release should stay collapsed
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) return@LaunchedEffect
        val midpoint = (minHeightPx + maxHeightPx) / 2f
        val target = if (state.currentPx > midpoint && scrollState.value == 0) maxHeightPx else minHeightPx
        if (state.currentPx != target) scope.launch { state.animateTo(target) }
    }

    return state
}

// intercepts scroll before the content's own ScrollState sees it: shrinks/grows the header by the
// drag delta first, and only lets the remainder reach the content once the header has hit a bound -
// so a fast fling still lands smoothly instead of the header and the list both jumping at once
@Composable
fun collapsingHeaderNestedScrollConnection(
    state: CollapsingHeaderState,
    scrollState: ScrollState,
): NestedScrollConnection {
    val scope = rememberCoroutineScope()
    return remember(state, scrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0
                if (!isScrollingDown && scrollState.value > 0) return Offset.Zero

                val previous = state.currentPx
                val target = (previous + delta).coerceIn(state.minHeightPx, state.maxHeightPx)
                val consumed = target - previous
                if (consumed.roundToInt() != 0) {
                    scope.launch { state.snapTo(target) }
                }
                val canConsume = !(isScrollingDown && target == state.minHeightPx)
                return if (canConsume) Offset(0f, consumed) else Offset.Zero
            }
        }
    }
}

// back button pinned top-start the whole time; the title itself slides from a large left-aligned
// line near the bottom of the expanded header up to a small line beside the button once collapsed,
// with the bar's own background fading in over the same stretch instead of staying always-opaque
@Composable
fun CollapsingDetailHeader(
    title: String,
    state: CollapsingHeaderState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = state.collapseFraction
    val solidAlpha = (fraction * 2f).coerceIn(0f, 1f)
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = solidAlpha)
    val titleScale = lerp(EXPANDED_TITLE_SCALE, COLLAPSED_TITLE_SCALE, fraction)
    val titleStartPadding =
        ExpandedTitleStartPadding + (CollapsedTitleStartPadding - ExpandedTitleStartPadding) * fraction
    val titleCenterY =
        ExpandedTitleCenterY + (CollapsedTitleCenterY - ExpandedTitleCenterY) * fraction

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(state.headerHeight)
                .background(backgroundColor)
                .zIndex(1f),
    ) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            HeaderTitle(
                title = title,
                scale = titleScale,
                startPadding = titleStartPadding,
                centerY = titleCenterY,
            )
            IconButton(
                onClick = onBack,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = BackButtonTopPadding)
                        .size(BackButtonSize),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back_rounded), contentDescription = "Back")
            }
        }
    }
}

// vertically centered on centerY via a fixed-height box + offset, rather than top/bottom alignment,
// so it lands on an exact pixel target (the back button's own center once collapsed) at any fraction
@Composable
private fun BoxScope.HeaderTitle(
    title: String,
    scale: Float,
    startPadding: Dp,
    centerY: Dp,
) {
    Box(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(TitleContainerHeight)
                .offset(y = centerY - TitleContainerHeight / 2)
                .padding(start = startPadding, end = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TitleTransformOrigin
                },
        )
    }
}
