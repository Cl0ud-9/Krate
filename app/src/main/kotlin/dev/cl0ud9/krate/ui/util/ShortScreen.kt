package dev.cl0ud9.krate.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

// under this height (a phone on its side) the tall header and nav bar would leave little room for content
private val SHORT_SCREEN = 480.dp

@Composable
fun isShortScreen(): Boolean =
    with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height
            .toDp()
    } < SHORT_SCREEN
