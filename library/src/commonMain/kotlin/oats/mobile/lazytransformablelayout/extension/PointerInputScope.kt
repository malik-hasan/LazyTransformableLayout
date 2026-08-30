package oats.mobile.lazytransformablelayout.extension

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln

suspend fun PointerInputScope.detectTransformGestures(
    onTransformStopped: (logZoomVelocity: Float, rotationVelocity: Float, panVelocity: Velocity, centroid: Offset) -> Unit,
    panZoomLock: Boolean = false,
    onTransform: (zoomFactor: Float, rotationDelta: Float, panDelta: Offset, centroid: Offset) -> Unit
) = awaitEachGesture {
    var lockedToPanZoom = false

    var pastTouchSlop = false
    val touchSlop = viewConfiguration.touchSlop
    var totalZoomBeforeTouchSlop = 1f
    var totalRotationBeforeTouchSlop = 0f
    var totalPanBeforeTouchSlop = Offset.Zero

    var lastCentroid = Offset.Zero
    var maxCentroidSize = 0f

    var lastEventUptimeMillis = 0L
    var previousPointerCount = 0
    var lastPointerCountChangeMillis = -1L
    var panVelocityBeforeLastReset = Velocity.Zero

    val logZoomVelocityTracker = VelocityTracker1D(true)
    val rotationVelocityTracker = VelocityTracker1D(true)
    val panVelocityTracker = VelocityTracker()

    awaitFirstDown(requireUnconsumed = false)
    do {
        val event = awaitPointerEvent()
        val changes = event.changes

        val canceled = changes.fastAny { it.isConsumed }
        if (!canceled) {
            val zoomFactor = event.calculateZoom()
            var rotationDelta = if (lockedToPanZoom) 0f else event.calculateRotation()
            val panDelta = event.calculatePan()
            val centroidSize = event.calculateCentroidSize(useCurrent = false)

            if (!pastTouchSlop) {
                totalZoomBeforeTouchSlop *= zoomFactor
                totalRotationBeforeTouchSlop += rotationDelta
                totalPanBeforeTouchSlop += panDelta

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
                maxCentroidSize = maxOf(maxCentroidSize, centroidSize)

                if (zoomFactor != 1f || rotationDelta != 0f || panDelta != Offset.Zero) {
                    onTransform(zoomFactor, rotationDelta, panDelta, centroid)
                }

                changes.fastForEach {
                    if (it.positionChanged()) it.consume()
                }

                val pointerCount = changes.count { it.pressed }
                val uptimeMillis = changes.first().uptimeMillis
                lastEventUptimeMillis = uptimeMillis
                if (previousPointerCount > 0 && pointerCount != previousPointerCount && pointerCount > 0) {
                    lastPointerCountChangeMillis = uptimeMillis
                    panVelocityTracker.run {
                        panVelocityBeforeLastReset = calculateVelocity()
                        resetTracking()
                    }
                }
                previousPointerCount = pointerCount

                logZoomVelocityTracker.addDataPoint(uptimeMillis, ln(zoomFactor))
                rotationVelocityTracker.addDataPoint(uptimeMillis, rotationDelta)
                event.calculateCentroid().takeIf { it.isSpecified }?.let {
                    panVelocityTracker.addPosition(uptimeMillis, it)
                }
            }
        }
    } while (!canceled && changes.fastAny { it.pressed })

    var logZoomVelocity = logZoomVelocityTracker.calculateVelocity()
    var rotationVelocity = if (lockedToPanZoom) 0f else rotationVelocityTracker.calculateVelocity()
    var panVelocity = if (lastPointerCountChangeMillis >= 0 && lastEventUptimeMillis - lastPointerCountChangeMillis < 40) {
        panVelocityBeforeLastReset
    } else {
        panVelocityTracker.calculateVelocity()
    }

    val zoomVelocityPixels = abs(logZoomVelocity) * maxCentroidSize
    val rotationVelocityPixels = abs(rotationVelocity).radians * maxCentroidSize
    val panVelocityPixels = hypot(panVelocity.x, panVelocity.y)

    val minZoomRotateFlingVelocity = 1000.dp.toPx()
    val zoomRotateNoiseFraction = 0.2f

    if (zoomVelocityPixels < maxOf(minZoomRotateFlingVelocity, maxOf(panVelocityPixels, rotationVelocityPixels) * zoomRotateNoiseFraction)) {
        logZoomVelocity = 0f
    }

    if (rotationVelocityPixels < maxOf(minZoomRotateFlingVelocity, maxOf(panVelocityPixels, zoomVelocityPixels) * zoomRotateNoiseFraction)) {
        rotationVelocity = 0f
    }

    if (panVelocityPixels < maxOf(300.dp.toPx(), maxOf(zoomVelocityPixels, rotationVelocityPixels) * 0.35f)) {
        panVelocity = Velocity.Zero
    }

    if (logZoomVelocity != 0f
        || rotationVelocity != 0f
        || panVelocity != Velocity.Zero
    ) onTransformStopped(logZoomVelocity, rotationVelocity, panVelocity, lastCentroid)
}
