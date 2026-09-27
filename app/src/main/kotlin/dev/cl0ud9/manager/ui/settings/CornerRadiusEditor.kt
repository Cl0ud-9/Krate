package dev.cl0ud9.manager.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.data.settings.DEFAULT_NAV_BAR_CORNER_RADIUS
import dev.cl0ud9.manager.data.settings.MAX_NAV_BAR_CORNER_RADIUS
import dev.cl0ud9.manager.data.settings.MIN_NAV_BAR_CORNER_RADIUS
import dev.cl0ud9.manager.ui.navigation.NavBarCompactContentHeight
import dev.cl0ud9.manager.ui.navigation.NavBarContentHeight
import dev.cl0ud9.manager.ui.navigation.SideInsets
import dev.cl0ud9.manager.ui.navigation.floatingNavBarMargins
import dev.cl0ud9.manager.ui.theme.ShapeCache
import kotlinx.coroutines.delay

private const val ENTRANCE_STAGGER_MS = 70
private const val ENTRANCE_RISE_PX = 60f
private const val VALUE_FADE_MS = 120

// full-screen editor for the floating bar's corners: a live bar sits exactly where the real one does,
// against the phone's own rounded screen corners, so the two can be matched by eye
@Composable
fun CornerRadiusEditor(onClose: () -> Unit) {
    val viewModel = rememberSettingsViewModel()
    val saved by viewModel.navBarCornerRadius.collectAsStateWithLifecycle()
    val compact by viewModel.navBarCompactMode.collectAsStateWithLifecycle()
    var radius by remember(saved) { mutableIntStateOf(saved) }
    val save = { value: Int -> viewModel.setNavBarCornerRadius(value) }
    val previewRadius by animateDpAsState(
        radius.dp,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "previewRadius",
    )
    val (side, bottom) = floatingNavBarMargins()
    // background edge to edge, content clear of the side insets (landscape camera cutout)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.background,
                ).windowInsetsPadding(SideInsets),
    ) {
        EditorTopBar(
            onBack = onClose,
            onDone = {
                save(radius)
                onClose()
            },
        )
        EditorHeading(modifier = Modifier.entrance(order = 0))
        Spacer(modifier = Modifier.weight(1f))
        RadiusControls(
            radius = radius,
            onChange = { radius = it },
            onChangeFinished = { save(radius) },
            onReset = {
                radius = DEFAULT_NAV_BAR_CORNER_RADIUS
                save(DEFAULT_NAV_BAR_CORNER_RADIUS)
            },
            modifier = Modifier.entrance(order = 1),
        )
        Surface(
            modifier =
                Modifier
                    .entrance(order = 2)
                    .padding(bottom = bottom)
                    .fillMaxWidth()
                    .height(if (compact) NavBarCompactContentHeight else NavBarContentHeight)
                    .padding(horizontal = side),
            shape = ShapeCache.corner(previewRadius),
            color = MaterialTheme.colorScheme.onBackground,
        ) {}
    }
}

@Composable
private fun EditorHeading(modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "Adjust corner radius",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        )
        Text(
            text = "Match the navigation bar's corners to your phone's own screen corners for a seamless look.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
    }
}

// each part rises and fades in a beat after the one above it when the editor opens
@Composable
private fun Modifier.entrance(order: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order * ENTRANCE_STAGGER_MS.toLong())
        progress.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        )
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * ENTRANCE_RISE_PX
    }
}

@Composable
private fun EditorTopBar(
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilledIconButton(
            onClick = onBack,
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
        ) {
            Icon(
                painterResource(R.drawable.ic_arrow_back_rounded),
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Button(
            onClick = onDone,
            shape = ShapeCache.smoothPill,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Icon(
                painterResource(R.drawable.ic_check_rounded),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Done")
        }
    }
}

@Composable
private fun RadiusControls(
    radius: Int,
    onChange: (Int) -> Unit,
    onChangeFinished: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 32.dp),
        shape = ShapeCache.smooth24,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Corner radius",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(
                    visible = radius != DEFAULT_NAV_BAR_CORNER_RADIUS,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                ) {
                    ResetButton {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onReset()
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painterResource(R.drawable.ic_rounded_corner_rounded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = radius.toFloat(),
                    onValueChange = { value ->
                        val stepped = value.toInt()
                        if (stepped != radius) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(stepped)
                    },
                    onValueChangeFinished = onChangeFinished,
                    valueRange = MIN_NAV_BAR_CORNER_RADIUS.toFloat()..MAX_NAV_BAR_CORNER_RADIUS.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                AnimatedContent(
                    targetState = radius,
                    transitionSpec = { fadeIn(tween(VALUE_FADE_MS)) togetherWith fadeOut(tween(VALUE_FADE_MS)) },
                    label = "radius-value",
                ) { value ->
                    Text(text = "$value dp", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ResetButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.height(32.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_restart_alt_rounded),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text("Reset", style = MaterialTheme.typography.labelMedium)
    }
}
