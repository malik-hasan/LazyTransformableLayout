package oats.mobile.lazytransformablelayout.utility

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln

// A pointer-count change this close to release is treated as "everyone let
// go together" rather than a deliberate change of gesture — fingers never
// physically lift in perfect sync. Compose's own VelocityTracker assumes
// movement stopped after a 40ms gap with no new data; this is the same
// ballpark with a little headroom for a couple of frames.
private const val PointerChangeDebounceMillis = 64L

suspend fun PointerInputScope.detectTransformGestures(
    onTransformStopped: (logZoomVelocity: Float, rotationVelocity: Float, panVelocity: Velocity, centroid: Offset) -> Unit,
    panZoomLock: Boolean = false,
    onTransform: (zoomFactor: Float, rotationDelta: Float, panDelta: Offset, centroid: Offset) -> Unit
) = awaitEachGesture {
    val touchSlop = viewConfiguration.touchSlop
    var pastTouchSlop = false
    var totalZoomBeforeTouchSlop = 1f
    var totalRotationBeforeTouchSlop = 0f
    var totalPanBeforeTouchSlop = Offset.Zero

    var previousPointerCount = 0
    var lastEventUptimeMillis = 0L
    // The centroid as of the last live onTransform call, handed to
    // onTransformStopped so the fling can keep anchoring zoom/rotation to
    // the same point the live gesture was using.
    var lastCentroid = Offset.Zero

    // Pan velocity means a different physical quantity depending on pointer
    // count (2-finger centroid movement vs 1-finger drag), and
    // calculateCentroid() jumps discontinuously the instant a pointer
    // lifts (the average shifts to exclude it) — so pan tracking resets on
    // every pointer-count change rather than blending across it.
    var panVelocityTracker = VelocityTracker()
    // Snapshot of the tracker right before its most recent reset, and when
    // that reset happened. If the gesture ends soon after, the fresh
    // tracker hasn't had time to mean anything — fingers always lift with
    // a slight stagger — so we fall back to this instead of discarding a
    // genuine multi-pointer pan's momentum.
    var panVelocityBeforeLastReset = Velocity.Zero
    var lastPointerCountChangeMillis = -1L

    val logZoomVelocityTracker = VelocityTracker1D(true)
    val rotationVelocityTracker = VelocityTracker1D(true)

    var lockedToPanZoom = false

    awaitFirstDown(requireUnconsumed = false)
    do {
        val event = awaitPointerEvent()
        val changes = event.changes

        val canceled = changes.fastAny { it.isConsumed }
        if (!canceled) {
            val zoomFactor = event.calculateZoom()
            var rotationDelta = if (lockedToPanZoom) 0f else event.calculateRotation()
            val panDelta = event.calculatePan()
            val pointerCount = changes.count { it.pressed }
            val uptimeMillis = changes.first().uptimeMillis
            lastEventUptimeMillis = uptimeMillis

            // Reset pan tracking when pointer count changes (but not on the
            // final lift to zero, which has no following phase to measure).
            if (pastTouchSlop && previousPointerCount > 0 && pointerCount != previousPointerCount && pointerCount > 0) {
                panVelocityBeforeLastReset = panVelocityTracker.calculateVelocity()
                lastPointerCountChangeMillis = uptimeMillis
                panVelocityTracker = VelocityTracker()
            }
            previousPointerCount = pointerCount

            if (!pastTouchSlop) {
                totalZoomBeforeTouchSlop *= zoomFactor
                totalRotationBeforeTouchSlop += rotationDelta
                totalPanBeforeTouchSlop += panDelta

                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotionBeforeTouchSlop = abs(1 - totalZoomBeforeTouchSlop) * centroidSize
                val rotationMotionBeforeTouchSlop = abs(totalRotationBeforeTouchSlop * PI.toFloat() * centroidSize / 180f)
                val panMotionBeforeTouchSlop = totalPanBeforeTouchSlop.getDistance()

                if (zoomMotionBeforeTouchSlop > touchSlop
                    || rotationMotionBeforeTouchSlop > touchSlop
                    || panMotionBeforeTouchSlop > touchSlop
                ) {
                    pastTouchSlop = true
                    lockedToPanZoom = panZoomLock && rotationMotionBeforeTouchSlop < touchSlop
                    if (lockedToPanZoom) rotationDelta = 0f
                }
            }

            if (pastTouchSlop) {
                val centroid = event.calculateCentroid(useCurrent = false)
                if (centroid.isSpecified) lastCentroid = centroid
                if (zoomFactor != 1f || rotationDelta != 0f || panDelta != Offset.Zero) {
                    onTransform(zoomFactor, rotationDelta, panDelta, centroid)
                }

                changes.fastForEach {
                    if (it.positionChanged()) it.consume()
                }

                logZoomVelocityTracker.addDataPoint(uptimeMillis, ln(zoomFactor))
                rotationVelocityTracker.addDataPoint(uptimeMillis, rotationDelta)
                event.calculateCentroid().takeIf { it.isSpecified }?.let {
                    panVelocityTracker.addPosition(uptimeMillis, it)
                }
            }
        }
    } while (!canceled && changes.fastAny { it.pressed })

    val logZoomVelocity = logZoomVelocityTracker.calculateVelocity()
    val rotationVelocity = if (lockedToPanZoom) 0f else rotationVelocityTracker.calculateVelocity()

    val finalPhaseDurationMillis = if (lastPointerCountChangeMillis >= 0) {
        lastEventUptimeMillis - lastPointerCountChangeMillis
    } else -1L
    val panVelocity = if (lastPointerCountChangeMillis >= 0 && finalPhaseDurationMillis < PointerChangeDebounceMillis) {
        panVelocityBeforeLastReset
    } else {
        panVelocityTracker.calculateVelocity()
    }

    if (logZoomVelocity != 0f
        || rotationVelocity != 0f
        || panVelocity != Velocity.Zero
    ) onTransformStopped(logZoomVelocity, rotationVelocity, panVelocity, lastCentroid)
}
