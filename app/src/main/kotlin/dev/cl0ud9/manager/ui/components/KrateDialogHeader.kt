package dev.cl0ud9.manager.ui.components

import androidx.compose.ui.graphics.painter.Painter

// what a dialog says up top: a small tag, an icon badge, a bold title and the plain explanation
class KrateDialogHeader(
    val tag: String,
    val icon: Painter,
    val title: String,
    val body: String,
)
