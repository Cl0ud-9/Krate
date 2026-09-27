package dev.cl0ud9.krate.ui.util

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

// the same padding with extra room at the bottom, for lists that scroll behind the nav bar
fun PaddingValues.plusBottom(extra: Dp): PaddingValues =
    PaddingValues(
        start = calculateLeftPadding(LayoutDirection.Ltr),
        top = calculateTopPadding(),
        end = calculateRightPadding(LayoutDirection.Ltr),
        bottom = calculateBottomPadding() + extra,
    )
