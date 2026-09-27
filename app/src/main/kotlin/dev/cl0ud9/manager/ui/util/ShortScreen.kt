package dev.cl0ud9.manager.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

// under this height (a phone on its side) the tall header and nav bar would leave little room for content
private const val SHORT_SCREEN_DP = 480

@Composable
fun isShortScreen(): Boolean = LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_DP
