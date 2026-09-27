package dev.cl0ud9.manager.ui.util

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout

// keeps a single-line text field at its resting height; its label briefly grows it on losing focus, nudging the page
fun Modifier.steadyHeight(): Modifier =
    composed {
        val resting = remember { IntArray(1) { -1 } }
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            if (resting[0] < 0) resting[0] = placeable.height
            layout(placeable.width, resting[0].coerceIn(constraints.minHeight, constraints.maxHeight)) {
                placeable.place(0, 0)
            }
        }
    }
