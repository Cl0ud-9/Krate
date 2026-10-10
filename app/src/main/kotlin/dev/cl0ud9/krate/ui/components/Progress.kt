package dev.cl0ud9.krate.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.text.TextStyle
import kotlin.math.abs
import kotlin.math.exp

// how far a download is, for every bar and ring that shows one: a share once the size is known, empty before the
// first bytes (so it fills from nothing rather than waving first and looking like it restarted), and null, no
// knowable pace, only when the size never comes
internal fun downloadFraction(
    bytes: Long,
    total: Long?,
): Float? {
    val size = total?.takeIf { it > 0 }
    return when {
        size != null -> (bytes / size.toFloat()).coerceIn(0f, 1f)
        bytes == 0L -> 0f
        else -> null
    }
}

// progress arrives in steps (a report every 256 KB, so a small app jumps 5% at a time); this glides between them.
// One follower chases the newest value each frame rather than an animation restarted per report: on a fast connection
// reports come quicker than frames, and restarting cancelled every glide before it drew, freezing the bar. It starts
// where the work already is, so a page opened mid-download doesn't sweep up from empty, a fresh start snaps back rather
// than sliding backwards, and with animations turned off in Android's settings it just follows
@Composable
internal fun rememberSmoothedProgress(target: Float): State<Float> {
    val latest = rememberUpdatedState(target)
    val shown = remember { mutableFloatStateOf(target) }
    LaunchedEffect(Unit) {
        val still = coroutineContext[MotionDurationScale]?.scaleFactor == 0f
        snapshotFlow { latest.value }.collect {
            var then = withFrameNanos { it }
            while (abs(latest.value - shown.floatValue) > SETTLED_WITHIN) {
                val goal = latest.value
                if (goal < shown.floatValue || still) {
                    shown.floatValue = goal
                } else {
                    val now = withFrameNanos { it }
                    val seconds = (now - then) / NANOS_PER_SECOND
                    then = now
                    // eases towards the goal: about two thirds of the way there every FOLLOW_SECONDS
                    shown.floatValue += (goal - shown.floatValue) * (1f - exp(-seconds / FOLLOW_SECONDS))
                }
            }
            shown.floatValue = latest.value
        }
    }
    return shown
}

private const val SETTLED_WITHIN = 0.0005f
private const val FOLLOW_SECONDS = 0.12f
private const val NANOS_PER_SECOND = 1_000_000_000f

// digits all one width, for figures that count up in place, so the words around them hold still
internal fun TextStyle.withTabularFigures(): TextStyle = copy(fontFeatureSettings = "tnum")
