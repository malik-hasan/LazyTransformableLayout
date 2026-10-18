package oats.mobile.lazytransformablelayout.extension

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln

private val MinZoomRotateFlingVelocity = 1000.dp

suspend fun PointerInputScope.detectTransformGestures(
    onTransformStopped: (centroid: Offset, rotationVelocity: Float, logZoomVelocity: Float, panVelocity: Velocity) -> Unit,
    panZoomLock: Boolean = false,
    onTransform: (centroid: Offset, rotationDelta: Float, zoomFactor: Float, panDelta: Offset) -> Unit
) = awaitEachGesture {
    var lockedToPanZoom = false

    var pastTouchSlop = false
    val touchSlop = viewConfiguration.touchSlop
    var totalRotationBeforeTouchSlop = 0f
    var totalZoomBeforeTouchSlop = 1f
    var totalPanBeforeTouchSlop = Offset.Zero

    var lastCentroid = Offset.Zero
    var maxCentroidSize = 0f

    var lastEventUptimeMillis = 0L
    var previousPointerCount = 0
    var lastPointerCountChangeMillis = -1L
    var panVelocityBeforeLastReset = Velocity.Zero

    val rotationVelocityTracker = VelocityTracker1D(true)
    val logZoomVelocityTracker = VelocityTracker1D(true)
    val panVelocityTracker = VelocityTracker()

    try {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val changes = event.changes
            val canceled = changes.fastAny { it.isConsumed }
            if (!canceled) {
                var rotationDelta = if (lockedToPanZoom) 0f else event.calculateRotation()
                val zoomFactor = event.calculateZoom()
                val panDelta = event.calculatePan()
                val centroidSize = event.calculateCentroidSize(useCurrent = false)

                if (!pastTouchSlop) {
                    totalRotationBeforeTouchSlop += rotationDelta
                    totalZoomBeforeTouchSlop *= zoomFactor
                    totalPanBeforeTouchSlop += panDelta

                    val rotationMotionBeforeTouchSlop = abs(totalRotationBeforeTouchSlop.radians * centroidSize)
                    val zoomMotionBeforeTouchSlop = abs(1 - totalZoomBeforeTouchSlop) * centroidSize
                    val panMotionBeforeTouchSlop = totalPanBeforeTouchSlop.getDistance()

                    if (rotationMotionBeforeTouchSlop > touchSlop
                        || zoomMotionBeforeTouchSlop > touchSlop
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

                    if (rotationDelta != 0f || zoomFactor != 1f || panDelta != Offset.Zero)
                        onTransform(centroid, rotationDelta, zoomFactor, panDelta)

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

                    rotationVelocityTracker.addDataPoint(uptimeMillis, rotationDelta)
                    logZoomVelocityTracker.addDataPoint(uptimeMillis, ln(zoomFactor))
                    event.calculateCentroid().takeIf { it.isSpecified }?.let {
                        panVelocityTracker.addPosition(uptimeMillis, it)
                    }
                }
            }
        } while (!canceled && changes.fastAny { it.pressed })
    } finally {
        var rotationVelocity = if (lockedToPanZoom) 0f else rotationVelocityTracker.calculateVelocity()
        var logZoomVelocity = logZoomVelocityTracker.calculateVelocity()
        var panVelocity = if (lastPointerCountChangeMillis >= 0 && lastEventUptimeMillis - lastPointerCountChangeMillis < 40) {
            panVelocityBeforeLastReset
        } else panVelocityTracker.calculateVelocity()

        val rotationVelocityPixels = abs(rotationVelocity).radians * maxCentroidSize
        val zoomVelocityPixels = abs(logZoomVelocity) * maxCentroidSize
        val panVelocityPixels = hypot(panVelocity.x, panVelocity.y)

        val minZoomRotateFlingVelocity = MinZoomRotateFlingVelocity.toPx()
        val zoomRotateNoiseFraction = 0.2f

        if (rotationVelocityPixels < maxOf(minZoomRotateFlingVelocity, maxOf(panVelocityPixels, zoomVelocityPixels) * zoomRotateNoiseFraction))
            rotationVelocity = 0f

        if (zoomVelocityPixels < maxOf(minZoomRotateFlingVelocity, maxOf(panVelocityPixels, rotationVelocityPixels) * zoomRotateNoiseFraction))
            logZoomVelocity = 0f

        if (panVelocityPixels < maxOf(300.dp.toPx(), maxOf(zoomVelocityPixels, rotationVelocityPixels) * 0.35f))
            panVelocity = Velocity.Zero

        // This must always be called to allow the overscroll effect to release
        onTransformStopped(lastCentroid, rotationVelocity, logZoomVelocity, panVelocity)
    }
}

suspend fun PointerInputScope.detectTwoFingerTapGestures(
    onTwoFingerTap: (centroid: Offset) -> Unit
) = awaitEachGesture {
    val firstDown = awaitFirstDown()
    val downPositions = mutableMapOf(firstDown.id to firstDown.position)
    withTimeout(viewConfiguration.longPressTimeoutMillis) {
        do {
            val changes = awaitPointerEvent().changes
            changes.fastForEach {
                if (it.changedToDown()) downPositions[it.id] = it.position
            }

            if (downPositions.size > 2
                || changes.fastAny { change ->
                    change.isConsumed || downPositions[change.id]?.let {
                        (change.position - it).getDistance() > viewConfiguration.touchSlop
                    } != false
                }
            ) return@withTimeout
        } while (changes.fastAny { it.pressed })

        if (downPositions.size == 2)
            onTwoFingerTap(downPositions.values.reduce(Offset::plus) / 2f)
    }
}

suspend fun PointerInputScope.detectDoubleTapDragGestures(
    onDoubleTapDragStopped: (logZoomVelocity: Float) -> Unit,
    onDoubleTapDrag: (zoomFactor: Float) -> Unit
) = awaitEachGesture {
    awaitFirstDown()
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        waitForUpOrCancellation()
    }?.let {

        withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown()
        }?.let { secondDown ->

            val zoomPerPixel = ln(16f) / size.height.coerceAtLeast(1)
            val velocityTracker = VelocityTracker1D(false)
            fun zoomBy(change: PointerInputChange, dragY: Float) {
                onDoubleTapDrag(exp(dragY * zoomPerPixel))
                change.run {
                    velocityTracker.addDataPoint(uptimeMillis, position.y)
                    consume()
                }
            }

            awaitTouchSlopOrCancellation(secondDown.id) { change, overSlop ->
                zoomBy(change, overSlop.y)
            }?.id?.let { dragPointerId ->
                if (verticalDrag(dragPointerId) {
                    zoomBy(it, it.positionChange().y)
                }) velocityTracker.calculateVelocity().takeIf {
                    abs(it) >= MinZoomRotateFlingVelocity.toPx()
                }?.let { velocity ->
                    onDoubleTapDragStopped(velocity * zoomPerPixel)
                }
            }
        }
    }
}
