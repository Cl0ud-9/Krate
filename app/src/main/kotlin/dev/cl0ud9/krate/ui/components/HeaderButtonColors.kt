package dev.cl0ud9.krate.ui.components

import androidx.compose.ui.graphics.Color

// a header button's fill and icon colours when solid, and its icon colour when frosted (it can keep its accent there)
data class HeaderButtonColors(
    val solid: Color,
    val content: Color,
    val glassContent: Color = content,
)
