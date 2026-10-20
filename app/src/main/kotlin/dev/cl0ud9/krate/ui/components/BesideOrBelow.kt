package dev.cl0ud9.krate.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// [main] with [side] beside it when there is room for main's longest word to stay whole next to side, and side
// underneath, at the end, when there isn't. Big text and display sizes squeeze a button beside a title until the title
// breaks letter by letter; this puts the button on its own line instead
@Composable
fun BesideOrBelow(
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
    centered: Boolean = false,
    main: @Composable () -> Unit,
    side: @Composable () -> Unit,
) {
    Layout(content = {
        main()
        side()
    }, modifier = modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // side can be empty (a button that is hidden for now): then main has the whole width to itself
        val sidePlaceable = measurables.getOrNull(1)?.measure(loose)
        val sideWidth = sidePlaceable?.width ?: 0
        val sideHeight = sidePlaceable?.height ?: 0
        val needed = measurables[0].minIntrinsicWidth(Constraints.Infinity) + gapPx + sideWidth
        val beside = sidePlaceable == null || needed <= constraints.maxWidth
        val gapUsed = if (sidePlaceable == null) 0 else gapPx
        val mainWidth = if (beside) constraints.maxWidth - sideWidth - gapUsed else constraints.maxWidth
        val mainPlaceable = measurables[0].measure(loose.copy(maxWidth = mainWidth))
        if (beside) {
            val height = maxOf(mainPlaceable.height, sideHeight)
            layout(constraints.maxWidth, height) {
                mainPlaceable.placeRelative(0, 0)
                sidePlaceable?.placeRelative(
                    constraints.maxWidth - sideWidth,
                    if (centered) (height - sideHeight) / 2 else 0,
                )
            }
        } else {
            val below = sidePlaceable ?: return@Layout layout(constraints.maxWidth, mainPlaceable.height) {}
            val height = mainPlaceable.height + gapPx / 2 + below.height
            layout(constraints.maxWidth, height) {
                mainPlaceable.placeRelative(0, 0)
                below.placeRelative(constraints.maxWidth - below.width, mainPlaceable.height + gapPx / 2)
            }
        }
    }
}
