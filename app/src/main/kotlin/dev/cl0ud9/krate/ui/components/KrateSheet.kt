package dev.cl0ud9.krate.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.ui.navigation.LocalLiquidGlass
import dev.cl0ud9.krate.ui.navigation.glassRim
import kotlin.math.roundToInt

// the gap round a floating sheet, and how near the top a sheet has to be pulled before it fills the width instead
private val FloatingGap = 10.dp
private const val FULL_HEIGHT_SHARE = 0.25f
private val FloatingShape = RoundedCornerShape(28.dp)

// Krate's bottom sheet. With liquid glass on it floats the way iOS sheets do: a gap at its sides and bottom and every
// corner rounded, still solid so its text stays crisp, growing out to the edges as it's pulled up to full height.
// With glass off it's Material's own sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrateSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    val floating = LocalLiquidGlass.current
    if (!floating) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = containerColor,
            content = content,
        )
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.floating(sheetState),
        shape = FloatingShape,
        containerColor = containerColor,
        // the handle and the bottom insets (navigation bar and keyboard) move inside, so the glass edge below wraps
        // exactly the sheet you see and the sheet still rises above the keyboard
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        // drawn on the sheet's own content, which moves with it: on the sheet's outer frame it traced a sheet-shaped
        // line from the top of the screen, since Material slides the sheet down inside that frame
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .glassRim(FloatingShape)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            content()
        }
    }
}

// narrows the sheet and lifts it off the bottom by the gap; the gap closes as the sheet's top nears the screen's.
// The gap depends on where the top is, the top on how tall the content is, and that on how its text wraps at the
// width the gap leaves: with a line that only just fits, that is a loop flipping between two answers for as long as
// the sheet is open. So the gap follows the sheet up, and is kept as it is once the sheet has come to rest
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Modifier.floating(state: SheetState): Modifier {
    val settledGap = remember { intArrayOf(NOT_SETTLED) }
    return layout { measurable, constraints ->
        val top = runCatching { state.requireOffset() }.getOrDefault(Float.MAX_VALUE)
        val share = (top / (constraints.maxHeight * FULL_HEIGHT_SHARE)).coerceIn(0f, 1f)
        val following = (FloatingGap.toPx() * share).roundToInt()
        if (settledGap[0] == NOT_SETTLED && state.currentValue == SheetValue.Expanded) settledGap[0] = following
        val gap = if (settledGap[0] == NOT_SETTLED) following else settledGap[0]
        val width = (constraints.maxWidth - gap * 2).coerceAtLeast(0)
        val placeable =
            measurable.measure(
                constraints.copy(minWidth = minOf(constraints.minWidth, width), maxWidth = width),
            )
        layout(placeable.width + gap * 2, placeable.height) { placeable.place(gap, -gap) }
    }
}

private const val NOT_SETTLED = -1
