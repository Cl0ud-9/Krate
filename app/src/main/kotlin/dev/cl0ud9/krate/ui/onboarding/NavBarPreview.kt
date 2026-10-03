package dev.cl0ud9.krate.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.navigation.GlassEdge
import dev.cl0ud9.krate.ui.navigation.glassSource
import dev.cl0ud9.krate.ui.navigation.liquidGlass
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.theme.krateGlow
import dev.cl0ud9.krate.ui.theme.rememberHeroGradient

// how long the preview takes to morph between a pill and a full-width bar; the page's own button folds with it
internal const val PREVIEW_MORPH_MS = 400

// widths of the pretend content lines in the bar preview
private val PLACEHOLDER_LINES = listOf(1f, 0.6f, 0.85f, 0.7f, 1f, 0.5f)

// the miniature header and its title
private val PREVIEW_HEADER_HEIGHT = 44.dp
private const val PREVIEW_TITLE_WIDTH = 0.35f

// lines in the accent colour, so the glass bar has some colour to bend
private val ACCENT_LINES = setOf(3, 5)

// a small phone screen drawn the way Krate will look: its header (a tint wash, or a glowing panel with glass on)
// over pretend content that runs on under the bar
@Composable
internal fun NavBarPreview(
    pill: Boolean,
    radius: Int,
    glass: Boolean,
) {
    val backdrop = if (glass) rememberLayerBackdrop() else null
    Surface(
        modifier = Modifier.fillMaxWidth().height(230.dp),
        shape = ShapeCache.rounded32,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box {
            Column(modifier = Modifier.fillMaxSize().glassSource(backdrop)) {
                PreviewHeader(glass = glass)
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PLACEHOLDER_LINES.forEachIndexed { index, fraction ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(fraction).height(12.dp),
                            shape = ShapeCache.pill,
                            color =
                                if (index in ACCENT_LINES) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                        ) {}
                    }
                }
            }
            PreviewBar(pill = pill, radius = radius, glass = backdrop)
        }
    }
}

// the tab header in miniature, drawn the way the app draws it
@Composable
private fun PreviewHeader(glass: Boolean) {
    val title: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize().padding(start = 20.dp), contentAlignment = Alignment.CenterStart) {
            Surface(
                modifier = Modifier.fillMaxWidth(PREVIEW_TITLE_WIDTH).height(14.dp),
                shape = ShapeCache.pill,
                color = MaterialTheme.colorScheme.primary,
            ) {}
        }
    }
    // the same strip either way, only what's behind the title changes: the tint wash, or with glass the glow
    val behind = if (glass) Modifier.krateGlow() else Modifier.background(rememberHeroGradient())
    Box(modifier = Modifier.fillMaxWidth().height(PREVIEW_HEADER_HEIGHT).then(behind)) { title() }
}

@Composable
private fun BoxScope.PreviewBar(
    pill: Boolean,
    radius: Int,
    glass: LayerBackdrop?,
) {
    val margin by animateDpAsState(if (pill) 14.dp else 0.dp, tween(PREVIEW_MORPH_MS), label = "previewMargin")
    val bottomRadius by animateDpAsState(
        if (pill) radius.dp else 0.dp,
        tween(PREVIEW_MORPH_MS),
        label = "previewBottom",
    )
    val shape =
        RoundedCornerShape(
            topStart = radius.dp,
            topEnd = radius.dp,
            bottomStart = bottomRadius,
            bottomEnd = bottomRadius,
        )
    val color = MaterialTheme.colorScheme.surfaceContainerLowest
    Surface(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(margin)
                .fillMaxWidth()
                .height(64.dp)
                .liquidGlass(
                    glass,
                    shape,
                    edge = if (pill) GlassEdge.ALL else GlassEdge.TOP,
                    color = color,
                    cornerRadius = radius.dp,
                ),
        shape = shape,
        color = if (glass != null) Color.Transparent else color,
    ) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            listOf(
                R.drawable.ic_nav_home_filled,
                R.drawable.ic_nav_apps_outline,
                R.drawable.ic_nav_update_outline,
            ).forEachIndexed {
                index,
                icon,
                ->
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint =
                        if (index ==
                            0
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}
