package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.model.DownloadStatus
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.ui.components.downloadFraction
import dev.cl0ud9.krate.ui.components.rememberSmoothedProgress

// how the ring sits round the icon, and the bounce an install lands with
// the icon's 56dp plus a gap and the wave on each side
private val RingSize = 76.dp
private const val TRACK_ALPHA = 0.28f
private const val LIGHT_LUMINANCE = 0.5f
private val CheckSize = 22.dp
private const val LAND_SCALE = 0.82f
private const val LAND_DAMPING = 0.4f
private const val LAND_STIFFNESS = 420f

// what the app's icon shows about an install going on right now
internal data class InstallMoment(
    // a download or install under way
    val busy: Boolean = false,
    // how far a download has got, 0 to 1; null while there's no way to tell
    val fraction: Float? = null,
    // an install just finished on this page
    val landed: Boolean = false,
)

internal fun installMomentFor(state: AppDetailsUiState): InstallMoment {
    val download = state.downloadStatus
    val install = state.installStatus
    // the same share the download bar shows, by the same rule
    val fraction =
        (download as? DownloadStatus.Downloading)?.let {
            downloadFraction(
                it.bytesDownloaded,
                it.totalBytes,
            )
        }
    val busy =
        download is DownloadStatus.Downloading ||
            download is DownloadStatus.Verifying ||
            install is InstallStatus.Installing ||
            install is InstallStatus.PreparingRollback ||
            install is InstallStatus.RollingBack ||
            install is InstallStatus.WaitingForUser
    return InstallMoment(busy = busy, fraction = fraction, landed = state.justInstalled)
}

// the app's icon with a ring that fills while it downloads and turns while it installs; when an install lands the icon
// drops in with a bounce, a haptic tick and a check on its corner. Only an install finishing here does that: coming
// back to a page that already shows "installed" stays still
@Composable
internal fun InstallHalo(
    moment: InstallMoment,
    // the app's own colour with glass on, where the card is tinted by its icon; the theme's otherwise
    accent: Color?,
    content: @Composable () -> Unit,
) {
    val badge = accent ?: MaterialTheme.colorScheme.primary
    val tick = if (accent == null) MaterialTheme.colorScheme.onPrimary else readableOn(accent)
    val haptics = LocalHapticFeedback.current
    val land = remember { Animatable(1f) }
    var celebrated by rememberSaveable { mutableStateOf(moment.landed) }
    LaunchedEffect(moment.landed) {
        if (!moment.landed) {
            celebrated = false
        } else if (!celebrated) {
            celebrated = true
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            land.snapTo(LAND_SCALE)
            land.animateTo(1f, spring(LAND_DAMPING, LAND_STIFFNESS))
        }
    }
    Box(
        modifier =
            Modifier.graphicsLayer {
                scaleX = land.value
                scaleY = land.value
            },
    ) {
        InstallRing(moment, accent)
        content()
        AnimatedVisibility(
            visible = moment.landed && !moment.busy,
            enter = scaleIn(spring(LAND_DAMPING, LAND_STIFFNESS)) + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(CheckSize)
                        .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        .padding(2.dp)
                        .background(badge, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = tick,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// Material's wavy progress round the icon, the way Google's own updates show it: filling while it downloads, a
// travelling wave while it installs. It sits just outside the icon, so nothing around it moves when it appears
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.InstallRing(
    moment: InstallMoment,
    accent: Color?,
) {
    val color = accent ?: WavyProgressIndicatorDefaults.indicatorColor
    val track = accent?.copy(alpha = TRACK_ALPHA) ?: WavyProgressIndicatorDefaults.trackColor
    AnimatedVisibility(
        visible = moment.busy,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.matchParentSize().wrapContentSize(unbounded = true),
    ) {
        val ring = Modifier.requiredSize(RingSize)
        // eased the same way as the download bar, so the two glide together
        val fraction = moment.fraction
        if (fraction != null) {
            val fill by rememberSmoothedProgress(fraction)
            CircularWavyProgressIndicator(progress = { fill }, modifier = ring, color = color, trackColor = track)
        } else {
            CircularWavyProgressIndicator(modifier = ring, color = color, trackColor = track)
        }
    }
}

// black or white, whichever reads on [colour]
private fun readableOn(colour: Color): Color = if (colour.luminance() > LIGHT_LUMINANCE) Color.Black else Color.White
