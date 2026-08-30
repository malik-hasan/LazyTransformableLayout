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
import co.touchlab.kermit.Logger
import oats.mobile.lazytransformablelayout.utility.TransformGestureTuning.Companion.CanvasLike
import oats.mobile.lazytransformablelayout.utility.TransformGestureTuning.Companion.MapLike
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln

private const val PointerChangeDebounceMillis = 40
private const val GestureTuningTag = "GestureTuning"

/**
 * Tuning for how [detectTransformGestures] classifies gesture axes and
 * filters incidental cross-axis jitter at release. Two different UX
 * philosophies are both legitimate depending on what kind of app this is,
 * and neither is "more correct" in general:
 *
 * - Map-like apps (Google/Apple Maps) treat rotation as a rare, deliberate
 *   mode: a two-finger pan or pinch should never accidentally rotate the
 *   content, even slightly, because unexpected rotation is disorienting.
 *   Use [MapLike] — it locks rotation out entirely, for the whole gesture
 *   (live transform and fling), unless the gesture is clearly rotational
 *   from the very start.
 *
 * - Canvas/photo-editor-like apps treat rotation as fully fluid and
 *   first-class, on equal footing with pan and zoom, and expect smooth
 *   transitions between them within one continuous gesture (e.g. rotate,
 *   then continue by panning, then release — the fling should reflect
 *   whichever was actually dominant at the end). Use [CanvasLike] (the
 *   default) — no axis is ever locked out; instead, incidental jitter on
 *   any axis is filtered using floors that scale with the gesture's own
 *   measured intensity.
 *
 * [MapLike] is deliberately defined as [CanvasLike] plus the rotation
 * lock, not as an unrelated set of numbers — the noise floors do the same
 * job in both cases (protecting pan from zoom/rotation jitter and vice
 * versa), the lock just additionally forecloses rotation entirely when
 * enabled. Both presets can be further customized via [copy].
 */
data class TransformGestureTuning(
    // Hard, one-time decision made at touch-slop crossing: if the gesture
    // didn't start out clearly rotational, rotation is zeroed for its
    // entire remaining duration — live transform and fling both. When
    // false, rotation is never locked out; the noise floors below are the
    // only protection against incidental rotation.
    val panZoomLock: Boolean,
    // Even correctly measured (no artifacts), a rotate or zoom gesture
    // leaves some real pan velocity from incidental hand drift, and
    // symmetrically a fast two-finger pan leaves incidental zoom/rotation
    // jitter — real fingers don't move in perfect lockstep. Each floor
    // below is a fixed baseline plus a fraction of whichever OTHER
    // signal is driving the gesture's intensity, since the amount of
    // incidental noise scales with how fast/aggressive the gesture is,
    // not a fixed amount. Tune against your own device: isolated slow AND
    // fast gestures on each axis should land under the relevant floor; a
    // deliberate combination should land over it.
    val minimumPanFlingVelocity: Float,
    val panNoiseFraction: Float,
    val minimumZoomFlingVelocity: Float,
    val zoomNoiseFraction: Float,
    val minimumRotationFlingVelocity: Float,
    val rotationNoiseFraction: Float
) {
    companion object {
        val CanvasLike = TransformGestureTuning(
            panZoomLock = false,
            minimumPanFlingVelocity = 300f,
            panNoiseFraction = 0.05f,
            minimumZoomFlingVelocity = 1000f,
            zoomNoiseFraction = 0.15f,
            minimumRotationFlingVelocity = 1000f,
            rotationNoiseFraction = 0.15f
        )

        val MapLike = CanvasLike.copy(panZoomLock = true)
    }
}

suspend fun PointerInputScope.detectTransformGestures(
    onTransformStopped: (logZoomVelocity: Float, rotationVelocity: Float, panVelocity: Velocity, centroid: Offset) -> Unit,
    tuning: TransformGestureTuning = TransformGestureTuning.CanvasLike,
    logTuningInfo: Boolean = true,
    onTransform: (zoomFactor: Float, rotationDelta: Float, panDelta: Offset, centroid: Offset) -> Unit
) = awaitEachGesture {
    val touchSlop = viewConfiguration.touchSlop
    var pastTouchSlop = false
    var totalZoomBeforeTouchSlop = 1f
    var totalRotationBeforeTouchSlop = 0f
    var totalPanBeforeTouchSlop = Offset.Zero

    var previousPointerCount = 0
    var lastEventUptimeMillis = 0L

    // Largest centroid size (finger spacing) seen this gesture — used to
    // convert zoom/rotation velocity into pixel-equivalent units, both for
    // scaling pan's noise floor and for comparing zoom/rotation against
    // their own floors on a consistent px/sec scale.
    var maxCentroidSize = 0f
    var lastCentroid = Offset.Zero

    // Pan velocity means a different physical quantity depending on pointer
    // count (2-finger centroid movement vs 1-finger drag), and
    // calculateCentroid() jumps discontinuously the instant a pointer
    // lifts (the average shifts to exclude it) — so pan tracking resets on
    // every pointer-count change rather than blending across it.
    var panVelocityTracker = VelocityTracker()
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
            val centroidSize = event.calculateCentroidSize(useCurrent = false)
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

                val zoomMotionBeforeTouchSlop = abs(1 - totalZoomBeforeTouchSlop) * centroidSize
                val rotationMotionBeforeTouchSlop = abs(totalRotationBeforeTouchSlop * PI.toFloat() * centroidSize / 180f)
                val panMotionBeforeTouchSlop = totalPanBeforeTouchSlop.getDistance()

                if (zoomMotionBeforeTouchSlop > touchSlop
                    || rotationMotionBeforeTouchSlop > touchSlop
                    || panMotionBeforeTouchSlop > touchSlop
                ) {
                    pastTouchSlop = true
                    lockedToPanZoom = tuning.panZoomLock && rotationMotionBeforeTouchSlop < touchSlop
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

                logZoomVelocityTracker.addDataPoint(uptimeMillis, ln(zoomFactor))
                rotationVelocityTracker.addDataPoint(uptimeMillis, rotationDelta)
                event.calculateCentroid().takeIf { it.isSpecified }?.let {
                    panVelocityTracker.addPosition(uptimeMillis, it)
                }
            }
        }
    } while (!canceled && changes.fastAny { it.pressed })

    val logZoomVelocityRaw = logZoomVelocityTracker.calculateVelocity()
    val rotationVelocityRaw = if (lockedToPanZoom) 0f else rotationVelocityTracker.calculateVelocity()

    val finalPhaseDurationMillis = if (lastPointerCountChangeMillis >= 0) {
        lastEventUptimeMillis - lastPointerCountChangeMillis
    } else -1L
    val usedFallbackSnapshot = lastPointerCountChangeMillis >= 0 && finalPhaseDurationMillis < PointerChangeDebounceMillis
    val panVelocityRaw = if (usedFallbackSnapshot) {
        panVelocityBeforeLastReset
    } else {
        panVelocityTracker.calculateVelocity()
    }

    val zoomVelocityPixels = abs(logZoomVelocityRaw) * maxCentroidSize
    val rotationVelocityPixels = abs(rotationVelocityRaw) * (PI.toFloat() / 180f) * maxCentroidSize
    val panVelocityPixels = hypot(panVelocityRaw.x, panVelocityRaw.y)

    // Pan's floor scales with whichever of zoom/rotation is more intense.
    val panGestureIntensityPixels = maxOf(zoomVelocityPixels, rotationVelocityPixels)
    val panScaledFloor = panGestureIntensityPixels * tuning.panNoiseFraction
    val effectiveMinimumPanVelocity = maxOf(tuning.minimumPanFlingVelocity, panScaledFloor)
    val panSuppressed = panVelocityPixels < effectiveMinimumPanVelocity
    val panVelocity = if (panSuppressed) Velocity.Zero else panVelocityRaw

    // Zoom and rotation's floors scale with pan's intensity, symmetrically
    // — a fast two-finger pan produces incidental zoom/rotation jitter the
    // same way a fast zoom/rotate produces incidental pan drift. When
    // panZoomLock has already zeroed rotationVelocityRaw, this is a no-op
    // (0 is trivially below any floor) rather than conflicting with it.
    val zoomScaledFloor = panVelocityPixels * tuning.zoomNoiseFraction
    val effectiveMinimumZoomVelocity = maxOf(tuning.minimumZoomFlingVelocity, zoomScaledFloor)
    val zoomSuppressed = zoomVelocityPixels < effectiveMinimumZoomVelocity
    val logZoomVelocity = if (zoomSuppressed) 0f else logZoomVelocityRaw

    val rotationScaledFloor = panVelocityPixels * tuning.rotationNoiseFraction
    val effectiveMinimumRotationVelocity = maxOf(tuning.minimumRotationFlingVelocity, rotationScaledFloor)
    val rotationSuppressed = rotationVelocityPixels < effectiveMinimumRotationVelocity
    val rotationVelocity = if (rotationSuppressed) 0f else rotationVelocityRaw

    if (logTuningInfo) {
        Logger.d(tag = GestureTuningTag) {
            "panPx=$panVelocityPixels (suppressed=$panSuppressed, floor=$effectiveMinimumPanVelocity " +
                    "[baseline=${tuning.minimumPanFlingVelocity}, scaled=$panScaledFloor]) | " +
                    "zoomPx=$zoomVelocityPixels (suppressed=$zoomSuppressed, floor=$effectiveMinimumZoomVelocity " +
                    "[baseline=${tuning.minimumZoomFlingVelocity}, scaled=$zoomScaledFloor]) | " +
                    "rotPx=$rotationVelocityPixels (suppressed=$rotationSuppressed, floor=$effectiveMinimumRotationVelocity " +
                    "[baseline=${tuning.minimumRotationFlingVelocity}, scaled=$rotationScaledFloor]) | " +
                    "panZoomLock=${tuning.panZoomLock} lockedToPanZoom=$lockedToPanZoom | " +
                    "fallback=$usedFallbackSnapshot finalPhaseMs=$finalPhaseDurationMillis | " +
                    "raw: logZoom=$logZoomVelocityRaw rot=$rotationVelocityRaw pan=$panVelocityRaw"
        }
    }

    if (logZoomVelocity != 0f
        || rotationVelocity != 0f
        || panVelocity != Velocity.Zero
    ) onTransformStopped(logZoomVelocity, rotationVelocity, panVelocity, lastCentroid)
}
