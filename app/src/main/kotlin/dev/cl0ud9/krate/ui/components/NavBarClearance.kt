package dev.cl0ud9.krate.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// how much of a tab's bottom edge the nav bar covers (the bar plus the system bar under it); tab content runs behind it
val LocalNavBarClearance = compositionLocalOf { 0.dp }

// the system navigation bar under a pushed page (no floating bar there): a gesture handle is thin, 3-button nav is not
val systemNavBarClearance: Dp
    @Composable get() = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
