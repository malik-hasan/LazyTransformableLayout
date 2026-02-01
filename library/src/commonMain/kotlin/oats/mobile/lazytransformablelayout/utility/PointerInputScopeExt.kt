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

suspend fun PointerInputScope.detectTransformGestures(
    onTransformStopped: (logZoomVelocity: Float, rotationVelocity: Float, panVelocity: Velocity) -> Unit,
    panZoomLock: Boolean = false,
    onTransform: (zoomFactor: Float, rotationDelta: Float, panDelta: Offset, centroid: Offset) -> Unit
) = awaitEachGesture {
    val touchSlop = viewConfiguration.touchSlop
    var pastTouchSlop = false
    var totalZoomBeforeTouchSlop = 1f
    var totalRotationBeforeTouchSlop = 0f
    var totalPanBeforeTouchSlop = Offset.Zero

    val logZoomVelocityTracker = VelocityTracker1D(true)
    val rotationVelocityTracker = VelocityTracker1D(true)
    val panVelocityTracker = VelocityTracker()

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
                if (zoomFactor != 1f || rotationDelta != 0f || panDelta != Offset.Zero) {
                    onTransform(zoomFactor, rotationDelta, panDelta, centroid)
                }

                changes.fastForEach {
                    if (it.positionChanged()) it.consume()
                }

                val uptimeMillis = changes.first().uptimeMillis
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
    val panVelocity = panVelocityTracker.calculateVelocity()

    if (logZoomVelocity != 0f
        || rotationVelocity != 0f
        || panVelocity != Velocity.Zero
    ) onTransformStopped(logZoomVelocity, rotationVelocity, panVelocity)
}
