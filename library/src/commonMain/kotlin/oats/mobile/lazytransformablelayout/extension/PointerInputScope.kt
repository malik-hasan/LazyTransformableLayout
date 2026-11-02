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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastForEach
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

private val MinZoomRotateFlingVelocity = 1000.dp
private const val MOUSE_ROTATION_DEGREES_PER_WIDTH = 180f

internal suspend fun PointerInputScope.detectWheelZoomGestures(
    onZoom: (position: Offset, zoomFactor: Float) -> Unit
) = awaitEachGesture {
    val event = awaitPointerEvent()
    if (event.type == PointerEventType.Scroll && event.changes.fastAll { !it.isConsumed }) {
        val scrollY = event.changes.fastFold(Offset.Zero) { acc, change ->
            acc + change.scrollDelta
        }.y

        if (scrollY != 0f) {
            onZoom(event.changes.first().position, 1.1f.pow(-scrollY))
            event.changes.fastForEach { it.consume() }
        }
    }
}

internal suspend fun PointerInputScope.detectTransformGestures(
    onTransformStopped: (centroid: Offset, rotationVelocity: Float, logZoomVelocity: Float, panVelocity: Velocity) -> Unit,
    onTransform: (centroid: Offset, rotationDelta: Float, zoomFactor: Float, panDelta: Offset) -> Unit
) = awaitEachGesture {
    var startEvent: PointerEvent
    do {
        startEvent = awaitPointerEvent()
    } while (startEvent.run {
        type != PointerEventType.PanStart
            && type != PointerEventType.ScaleStart
            && !(changes.fastAll { it.changedToDownIgnoreConsumed() }
                && (changes.first().type != PointerType.Mouse || buttons.isPrimaryPressed)
            )
    })

    if (startEvent.run {
        type == PointerEventType.PanStart || type == PointerEventType.ScaleStart
    }) {
        var event = startEvent

        var totalPan = Offset.Zero
        val panVelocityTracker = VelocityTracker()
        val logZoomVelocityTracker = VelocityTracker1D(true)

        val endType = if (startEvent.type == PointerEventType.PanStart) {
            PointerEventType.PanEnd
        } else PointerEventType.ScaleEnd

        try {
            while (true) {
                val change = event.changes.first()

                val panDelta = change.historical.fastFold(-change.panOffset) { acc, historical ->
                    acc - historical.panOffset
                }

                val zoomFactor = change.historical.fastFold(change.scaleFactor) { acc, historical ->
                    acc * historical.scaleFactor
                }

                if (panDelta != Offset.Zero || zoomFactor != 1f)
                    onTransform(change.position, 0f, zoomFactor, panDelta)

                event.changes.fastForEach { it.consume() }

                totalPan += panDelta
                panVelocityTracker.addPosition(change.uptimeMillis, totalPan)
                logZoomVelocityTracker.addDataPoint(change.uptimeMillis, ln(zoomFactor))

                if (event.type == endType) break

                do {
                    event = awaitPointerEvent()
                } while (event.type != endType
                    && event.type != PointerEventType.PanMove
                    && event.type != PointerEventType.ScaleChange
                )
            }
        } finally {
            stopWithFlingThresholds(
                onTransformStopped = onTransformStopped,
                centroid = event.changes.first().position,
                rotationVelocity = 0f,
                logZoomVelocity = logZoomVelocityTracker.calculateVelocity(),
                panVelocity = panVelocityTracker.calculateVelocity(),
                maxCentroidSize = size.width.coerceAtMost(size.height) / 2f
            )
        }
    } else {
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var totalRotationBeforeTouchSlop = 0f
        var totalZoomBeforeTouchSlop = 1f
        var totalPanBeforeTouchSlop = Offset.Zero

        var lastCentroid = Offset.Unspecified
        var maxCentroidSize = 0f

        var lastEventUptimeMillis = 0L
        var previousPointerCount = 0
        var lastPointerCountChangeMillis = -1L
        var panVelocityBeforeLastReset = Velocity.Zero

        val rotationVelocityTracker = VelocityTracker1D(true)
        val logZoomVelocityTracker = VelocityTracker1D(true)
        val panVelocityTracker = VelocityTracker()

        val mouseRotating = startEvent.changes.first().type == PointerType.Mouse
            && startEvent.keyboardModifiers.isShiftPressed

        try {
            do {
                val event = awaitPointerEvent()
                val changes = event.changes
                val canceled = changes.fastAny { it.isConsumed }
                if (!canceled) {
                    val rotationDelta: Float
                    val zoomFactor: Float
                    val panDelta: Offset
                    val centroidSize: Float
                    if (mouseRotating) {
                        rotationDelta = event.calculatePan().x / size.width * MOUSE_ROTATION_DEGREES_PER_WIDTH
                        zoomFactor = 1f
                        panDelta = Offset.Zero
                        centroidSize = size.width / MOUSE_ROTATION_DEGREES_PER_WIDTH.radians
                    } else {
                        rotationDelta = event.calculateRotation()
                        zoomFactor = event.calculateZoom()
                        panDelta = event.calculatePan()
                        centroidSize = event.calculateCentroidSize(useCurrent = false)
                    }

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
                        }
                    }

                    if (pastTouchSlop) {
                        val centroid = if (mouseRotating) {
                            Offset.Unspecified
                        } else event.calculateCentroid(false)
                            .also { lastCentroid = it }

                        maxCentroidSize = maxOf(maxCentroidSize, centroidSize)

                        if (rotationDelta != 0f || zoomFactor != 1f || panDelta.x != 0f || panDelta.y != 0f)
                            onTransform(centroid, rotationDelta, zoomFactor, panDelta)

                        var pointerCount = 0
                        changes.fastForEach {
                            if (it.positionChanged()) it.consume()
                            if (it.pressed) pointerCount++
                        }

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
                        if (!mouseRotating)
                            event.calculateCentroid().takeIf { it.isSpecified }?.let {
                                panVelocityTracker.addPosition(uptimeMillis, it)
                            }
                    }
                }
            } while (!canceled && changes.fastAny { it.pressed })
        } finally {
            // This must always be called to allow the overscroll effect to release
            stopWithFlingThresholds(
                onTransformStopped = onTransformStopped,
                centroid = lastCentroid,
                rotationVelocity = rotationVelocityTracker.calculateVelocity(),
                logZoomVelocity = logZoomVelocityTracker.calculateVelocity(),
                panVelocity = if (lastPointerCountChangeMillis >= 0 && lastEventUptimeMillis - lastPointerCountChangeMillis < 40) {
                    panVelocityBeforeLastReset
                } else panVelocityTracker.calculateVelocity(),
                maxCentroidSize = maxCentroidSize
            )
        }
    }
}

private fun PointerInputScope.stopWithFlingThresholds(
    onTransformStopped: (centroid: Offset, rotationVelocity: Float, logZoomVelocity: Float, panVelocity: Velocity) -> Unit,
    centroid: Offset,
    rotationVelocity: Float,
    logZoomVelocity: Float,
    panVelocity: Velocity,
    maxCentroidSize: Float
) {
    val rotationVelocityPixels = abs(rotationVelocity).radians * maxCentroidSize
    val zoomVelocityPixels = abs(logZoomVelocity) * maxCentroidSize
    val panVelocityPixels = hypot(panVelocity.x, panVelocity.y)

    val minZoomRotateFlingVelocity = MinZoomRotateFlingVelocity.toPx()
    val zoomRotateNoiseFraction = 0.2f

    onTransformStopped(
        centroid,
        rotationVelocity.takeUnless {
            rotationVelocityPixels < maxOf(
                minZoomRotateFlingVelocity,
                maxOf(panVelocityPixels, zoomVelocityPixels) * zoomRotateNoiseFraction
            )
        } ?: 0f,
        logZoomVelocity.takeUnless {
            zoomVelocityPixels < maxOf(
                minZoomRotateFlingVelocity,
                maxOf(panVelocityPixels, rotationVelocityPixels) * zoomRotateNoiseFraction
            )
        } ?: 0f,
        panVelocity.takeUnless {
            panVelocityPixels < maxOf(
                300.dp.toPx(),
                maxOf(zoomVelocityPixels, rotationVelocityPixels) * 0.35f
            )
        } ?: Velocity.Zero
    )
}

internal suspend fun PointerInputScope.detectTapSequenceGestures(
    onTwoFingerTap: () -> Unit,
    onSecondaryDoubleClick: (position: Offset) -> Unit,
    onDoubleTapDragStopped: (logZoomVelocity: Float) -> Unit,
    onDoubleTapDrag: (zoomFactor: Float) -> Unit
) = awaitEachGesture {
    var downEvent: PointerEvent
    do {
        downEvent = awaitPointerEvent()
    } while (!downEvent.changes.fastAll { it.changedToDown() })

    val firstDown = downEvent.changes.first()
    if (firstDown.type == PointerType.Mouse && downEvent.buttons.isSecondaryPressed) {
        firstDown.consume()
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation()
        } ?: withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            var secondDownEvent: PointerEvent
            do {
                secondDownEvent = awaitPointerEvent()
            } while (!secondDownEvent.changes.fastAll { it.changedToDown() })
            secondDownEvent
        }?.takeIf { it.buttons.isSecondaryPressed }?.changes?.first()?.takeIf {
            (it.position - firstDown.position).getDistance() <= viewConfiguration.touchSlop
        }?.let { secondDown ->
            secondDown.consume()
            onSecondaryDoubleClick(secondDown.position)
        }
    } else {
        val downPositions = mutableMapOf(firstDown.id to firstDown.position)
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
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
                ) return@withTimeoutOrNull false
            } while (changes.fastAny { it.pressed })
            true
        }?.let {
            if (downPositions.size == 2) {
                onTwoFingerTap()
            } else withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
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
}
