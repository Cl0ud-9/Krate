package dev.cl0ud9.krate.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

private const val DEFAULT_SPACING = 8
private const val STACK_GAP = 8

// buttons side by side, pushed to the end, when they all fit on one line; when they don't (a large font, a narrow
// screen, a long label) they stack instead, each as wide as the row with the main one, the last, on top. Never a
// button stranded alone on one line while the other sits at the far side of the next
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    spacing: Int = DEFAULT_SPACING,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.dp.roundToPx()
        val stackGap = STACK_GAP.dp.roundToPx()
        val width = constraints.maxWidth
        val natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val sideBySide = natural.sum() + gap * (measurables.size - 1).coerceAtLeast(0) <= width
        if (sideBySide) {
            val items =
                measurables.mapIndexed {
                    i,
                    m,
                    ->
                    m.measure(Constraints(minWidth = natural[i], maxWidth = natural[i]))
                }
            val height = items.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                var x = width - (items.sumOf { it.width } + gap * (items.size - 1).coerceAtLeast(0))
                items.forEach {
                    it.place(x, (height - it.height) / 2)
                    x += it.width + gap
                }
            }
        } else {
            val items = measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            val ordered = items.asReversed()
            layout(width, ordered.sumOf { it.height } + stackGap * (ordered.size - 1).coerceAtLeast(0)) {
                var y = 0
                ordered.forEach {
                    it.place(0, y)
                    y += it.height + stackGap
                }
            }
        }
    }
}
