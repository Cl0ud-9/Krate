// Adapted from the Backdrop library's catalog app by Kyant (github.com/Kyant0/AndroidLiquidGlass),
// Apache License 2.0. See NOTICE.
package dev.cl0ud9.krate.ui.navigation

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val SETTLE_FRACTION = 0.025f

// a value that follows a finger or a target on a spring, with how hard it's pressed and how fast it moves, so glass
// can swell under a touch and stretch as it slides
@Suppress("LongParameterList")
internal class DampedDragAnimation(
    private val scope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    private val pressedScale: Float,
    private val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDrag: DampedDragAnimation.(dragAmount: Offset) -> Unit,
) {
    private val valueSpec = spring(1f, 1000f, VALUE_THRESHOLD)
    private val velocitySpec = spring(0.5f, 300f, VALUE_THRESHOLD * 10f)
    private val pressSpec = spring(1f, 1000f, SCALE_THRESHOLD)
    private val scaleXSpec = spring(0.6f, 250f, SCALE_THRESHOLD)
    private val scaleYSpec = spring(0.7f, 250f, SCALE_THRESHOLD)

    private val valueAnimation = Animatable(initialValue, VALUE_THRESHOLD)
    private val velocityAnimation = Animatable(0f, VELOCITY_THRESHOLD)
    private val pressAnimation = Animatable(0f, SCALE_THRESHOLD)
    private val scaleXAnimation = Animatable(1f, SCALE_THRESHOLD)
    private val scaleYAnimation = Animatable(1f, SCALE_THRESHOLD)
    private val mutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier =
        Modifier.pointerInput(Unit) {
            inspectDragGestures(
                onDragStart = { down ->
                    onDragStarted(down.position)
                    press()
                },
                onDragEnd = {
                    onDragStopped()
                    release()
                },
                onDragCancel = {
                    onDragStopped()
                    release()
                },
            ) { _, dragAmount -> onDrag(dragAmount) }
        }

    fun press() {
        velocityTracker.resetTracking()
        scope.launch {
            launch { pressAnimation.animateTo(1f, pressSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYSpec) }
        }
    }

    fun release() {
        scope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * SETTLE_FRACTION
                snapshotFlow { valueAnimation.value }
                    .filter {
                        abs(
                            it - valueAnimation.targetValue,
                        ) < threshold
                    }.first()
            }
            launch { pressAnimation.animateTo(0f, pressSpec) }
            launch { scaleXAnimation.animateTo(1f, scaleXSpec) }
            launch { scaleYAnimation.animateTo(1f, scaleYSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        scope.launch { valueAnimation.animateTo(target, valueSpec) { updateVelocity() } }
    }

    // glides to a value with a press and release on the way, the little swell a tab switch gets
    fun animateToValue(value: Float) {
        scope.launch {
            mutex.mutate {
                press()
                launch { valueAnimation.animateTo(value.coerceIn(valueRange), valueSpec) }
                if (velocity != 0f) launch { velocityAnimation.animateTo(0f, velocitySpec) }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val target = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        scope.launch { velocityAnimation.animateTo(target, velocitySpec) }
    }

    private companion object {
        const val VALUE_THRESHOLD = 0.001f
        const val VELOCITY_THRESHOLD = 5f
        const val SCALE_THRESHOLD = 0.001f
    }
}

// a soft light under the finger while it's on the glass, drifting back when it lifts
internal class InteractiveHighlight(
    private val scope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {
    private val pressSpec = spring(0.5f, 300f, 0.001f)
    private val positionSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    // 0 at rest, springing to 1 while a finger is down
    val progress: Float get() = pressAnimation.value

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressAnimation.value
            if (progress > 0f) {
                val spot = position(size, positionAnimation.value)
                val center = Offset(spot.x.coerceIn(0f, size.width), spot.y.coerceIn(0f, size.height))
                drawRect(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color.White.copy(alpha = GlassTokens.PRESS_GLOW * progress),
                                    Color.Transparent,
                                ),
                            center = center,
                            radius = size.minDimension * GlassTokens.PRESS_GLOW_SPREAD,
                        ),
                    blendMode = BlendMode.Plus,
                )
            }
            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(scope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    scope.launch {
                        launch { pressAnimation.animateTo(1f, pressSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = { settle() },
                onDragCancel = { settle() },
            ) { change, _ -> scope.launch { positionAnimation.snapTo(change.position) } }
        }

    private fun settle() {
        scope.launch {
            launch { pressAnimation.animateTo(0f, pressSpec) }
            launch { positionAnimation.animateTo(startPosition, positionSpec) }
        }
    }
}

// watches a finger go down, move and lift without taking the touch from the taps underneath
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val up = trackDrag(initialDown.id) { onDrag(it, it.positionChange()) }
        if (up == null) onDragCancel() else onDragEnd(up)
    }
}

@Suppress("ReturnCount")
private suspend inline fun AwaitPointerEventScope.trackDrag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    if (currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

@Suppress("ReturnCount")
private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (change.changedToUpIgnoreConsumed()) {
            pointer = event.changes.fastFirstOrNull { it.pressed }?.id ?: return change
        } else if (change.previousPosition != change.position) {
            return change
        }
    }
}
