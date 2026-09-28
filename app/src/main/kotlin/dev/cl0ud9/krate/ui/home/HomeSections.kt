package dev.cl0ud9.krate.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.components.AppIconAvatar
import dev.cl0ud9.krate.ui.components.BusyButtonContent
import dev.cl0ud9.krate.ui.components.StatTile
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.pressScale
import dev.cl0ud9.krate.ui.util.rememberDebouncedOnClick
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLine
import kotlinx.coroutines.delay

private val APP_TILE_SIZE = 60.dp
private const val APPEAR_STAGGER_MS = 60L
private const val APPEAR_RISE_PX = 48f

// how every tappable card and row on Home reacts: a slight shrink, a ripple rounded to its shape, double taps ignored
@Composable
internal fun Modifier.homeTappable(
    shape: Shape,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val debounced = rememberDebouncedOnClick(onClick = onClick)
    return pressScale(interactionSource)
        .clip(shape)
        .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = debounced)
}

// "am I up to date, and what should I do about it" - the whole card is the action: open Updates, or look again
@Composable
internal fun StatusHeroCard(
    pendingUpdateCount: Int,
    checking: Boolean,
    onViewUpdates: () -> Unit,
    onCheckAgain: () -> Unit,
) {
    val upToDate = pendingUpdateCount == 0
    val colors = MaterialTheme.colorScheme
    val container = if (upToDate) colors.primaryContainer else colors.tertiaryContainer
    val content = if (upToDate) colors.onPrimaryContainer else colors.onTertiaryContainer
    val action = if (upToDate) onCheckAgain else onViewUpdates
    Card(
        modifier = Modifier.fillMaxWidth().homeTappable(ShapeCache.rounded28, action),
        shape = ShapeCache.rounded28,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                painter =
                    painterResource(
                        if (upToDate) R.drawable.ic_check_circle_rounded else R.drawable.ic_system_update_alt_rounded,
                    ),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = rememberKrateLine(if (upToDate) Moment.ALL_CAUGHT_UP else Moment.UPDATES_WAITING),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    when {
                        upToDate -> "Every installed app is on its latest version."
                        pendingUpdateCount == 1 -> "1 update is ready to install."
                        else -> "$pendingUpdateCount updates are ready to install."
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
            // stays enabled while checking (a second tap is ignored) so the button never flashes to a dimmed colour
            Button(
                onClick = { if (!checking) action() },
                colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = container),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                BusyButtonContent(
                    busy = checking,
                    text = if (upToDate) "Check again" else "View updates",
                    icon = if (upToDate) R.drawable.ic_refresh_rounded else R.drawable.ic_arrow_forward_rounded,
                )
            }
        }
    }
}

// the apps on this phone, one tap from their pages; a dot marks the ones with an update waiting
@Composable
internal fun YourAppsRow(
    apps: List<HomeApp>,
    onOpenApp: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Your apps",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSeeAll) { Text("See all") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(apps, key = { it.app.id }) { item -> AppTile(item = item, onClick = { onOpenApp(item.app.id) }) }
        }
    }
}

@Composable
private fun AppTile(
    item: HomeApp,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(76.dp)
                .homeTappable(ShapeCache.rounded16, onClick)
                .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            AppIconAvatar(app = item.app, size = APP_TILE_SIZE)
            if (item.hasUpdate) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(2.dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                )
            }
        }
        Text(
            text = item.app.displayName,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// Home's cards rise into place one after another the first time it opens; coming back from another tab
// restores them already in place instead of replaying it
@Composable
internal fun HomeAppear(
    order: Int,
    content: @Composable () -> Unit,
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay(order * APPEAR_STAGGER_MS)
            progress.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            )
            shown = true
        }
    }
    Box(
        modifier =
            Modifier.graphicsLayer {
                alpha = progress.value.coerceIn(0f, 1f)
                translationY = (1f - progress.value) * APPEAR_RISE_PX
            },
    ) { content() }
}

// catalog, installed and waiting counts; equal heights whatever the screen width, so no tile stretches taller
@Composable
internal fun StatTiles(
    counts: Triple<Int, Int, Int>,
    onOpenApps: () -> Unit,
    onOpenUpdates: () -> Unit,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val tile = Modifier.weight(1f).fillMaxHeight()
        StatTile(label = "Apps", value = counts.first.toString(), modifier = tile, onClick = onOpenApps)
        StatTile(label = "Installed", value = counts.second.toString(), modifier = tile, onClick = onOpenApps)
        StatTile(label = "Updates", value = counts.third.toString(), modifier = tile, onClick = onOpenUpdates)
    }
}
