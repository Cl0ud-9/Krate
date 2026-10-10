package dev.cl0ud9.krate.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.theme.krateGlow
import dev.cl0ud9.krate.ui.theme.rememberHeroGradient

// no boxed top chrome, just the title on the background, with the page on a rounded-top panel below it
private val TabContentPanelRadius = 28.dp

// one layout whether liquid glass is on or off, so the switch never moves anything: only what's behind the title
// changes, a tint wash fading out by mid-screen, or with glass on a soft two-colour glow
@Composable
internal fun TabFrame(
    glass: Boolean,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    var headerHeight by remember { mutableIntStateOf(0) }
    Box(modifier = Modifier.fillMaxSize()) {
        if (glass) {
            // as tall as the header plus the panel's rounded corners, so the glow also fills the gaps beside them
            val glowHeight = with(LocalDensity.current) { headerHeight.toDp() } + TabContentPanelRadius
            Box(modifier = Modifier.fillMaxWidth().height(glowHeight).krateGlow())
        }
        Scaffold(
            modifier = if (glass) Modifier else Modifier.background(rememberHeroGradient()),
            topBar = {
                // content keeps clear of the side insets (landscape camera cutout); backgrounds run edge to edge
                Box(
                    modifier =
                        Modifier
                            .onSizeChanged { headerHeight = it.height }
                            .windowInsetsPadding(SideInsets),
                ) { header() }
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding())
                        // flying icons going back to this tab pass under the header from here down
                        .onGloballyPositioned { IconFlight.panelTop = it.positionInWindow().y },
                color = MaterialTheme.colorScheme.surface,
                shape = ShapeCache.contentPanel(TabContentPanelRadius),
            ) {
                Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(SideInsets)) { content() }
            }
        }
    }
}
