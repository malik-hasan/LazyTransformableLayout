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
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln

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

                    val rotationMotionBeforeTouchSlop = abs(totalRotationBeforeTouchSlop * PI.toFloat() * centroidSize / 180f)
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

        val minZoomRotateFlingVelocity = 1000.dp.toPx()
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

internal suspend fun PointerInputScope.detectZoomTapGestures(
    onDoubleTapDrag: (centroid: Offset, zoomFactor: Float) -> Unit,
    onTwoFingerTap: (centroid: Offset) -> Unit
) = awaitEachGesture {
    val firstDown = awaitFirstDown()

    val touchSlop = viewConfiguration.touchSlop
    val downPositions = mutableMapOf(firstDown.id to firstDown.position)
    val isTap = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var isTap: Boolean
        do {
            val changes = awaitPointerEvent().changes
            changes.fastForEach {
                if (it.changedToDown()) downPositions[it.id] = it.position
            }
            isTap = downPositions.size <= 2 && changes.fastAll { change ->
                !change.isConsumed && downPositions[change.id]?.let {
                    (change.position - it).getDistance() <= touchSlop
                } == true
            }
        } while (isTap && changes.fastAny { it.pressed })
        isTap
    } ?: false

    if (!isTap) return@awaitEachGesture

    if (downPositions.size == 2) {
        onTwoFingerTap(downPositions.values.reduce(Offset::plus) / 2f)
        return@awaitEachGesture
    }

    val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
        awaitFirstDown()
    } ?: return@awaitEachGesture

    val zoomPerPixel = ln(2f) / DOUBLE_TAP_DRAG_DOUBLING_DISTANCE.toPx()
    var dragging = false
    var previousY = secondDown.position.y
    do {
        val event = awaitPointerEvent()
        val change = event.changes.fastFirstOrNull { it.id == secondDown.id }
        if (change == null || change.isConsumed || event.changes.size > 1) return@awaitEachGesture

        if (!dragging && (change.position - secondDown.position).getDistance() > touchSlop) dragging = true
        if (dragging) {
            onDoubleTapDrag(secondDown.position, exp((change.position.y - previousY) * zoomPerPixel))
            change.consume()
        }
        previousY = change.position.y
    } while (change.pressed)
}

private val DOUBLE_TAP_DRAG_DOUBLING_DISTANCE = 100.dp
