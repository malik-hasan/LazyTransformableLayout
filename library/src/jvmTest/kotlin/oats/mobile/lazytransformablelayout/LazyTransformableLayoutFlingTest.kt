package oats.mobile.lazytransformablelayout

import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import oats.mobile.lazytransformablelayout.extension.nearestPointOnSegment
import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LazyTransformableLayoutFlingTest {

    private data class Frame(val offset: Offset, val angle: Float, val scale: Float)

    private val viewportWidth = 1080
    private val viewportHeight = 2000
    private val layoutBounds = Rect(0f, 0f, 10_000f, 6_000f)

    private fun state(
        rotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
        zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
        initialOffset: Offset = Offset(3_000f, 2_000f),
        initialAngle: Float = 0f,
        initialScale: Float = 1f,
        rotationFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec(),
        zoomFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec()
    ) = LazyTransformableLayoutState(
        layoutBounds = layoutBounds,
        rotationBounds = rotationBounds,
        zoomBounds = zoomBounds,
        initialOffset = initialOffset,
        initialAngle = initialAngle,
        initialScale = initialScale,
        rotationFlingDecay = rotationFlingDecay,
        zoomFlingDecay = zoomFlingDecay
    ).apply {
        acceptConstraints(Constraints.fixed(viewportWidth, viewportHeight))
    }

    private fun LazyTransformableLayoutState.runFling(
        rotationVelocity: Float = 0f,
        logZoomVelocity: Float = 0f,
        panVelocity: Velocity = Velocity.Zero,
        centroid: Offset = Offset(viewportWidth / 2f, viewportHeight / 2f),
        maxFrames: Int = 2_000,
        onFrame: (index: Int) -> Unit = {}
    ): List<Frame> = runBlocking {
        val clock = BroadcastFrameClock()
        val frames = mutableListOf(Frame(offset, angle, scale))
        val job = launch(clock) {
            fling(rotationVelocity, logZoomVelocity, panVelocity, centroid)
        }
        var frameTimeNanos = 0L
        while (job.isActive && frames.size <= maxFrames) {
            yield()
            if (clock.hasAwaiters) {
                frameTimeNanos += FRAME_NANOS
                clock.sendFrame(frameTimeNanos)
                frames += Frame(offset, angle, scale)
                onFrame(frames.lastIndex)
            }
        }
        job.cancelAndJoin()
        frames
    }

    private fun List<Frame>.lastChangeIndex(property: (Frame) -> Any) =
        indices.drop(1).lastOrNull { property(this[it]) != property(this[it - 1]) } ?: 0

    private fun List<Frame>.changeCount(property: (Frame) -> Any) =
        zipWithNext().count { (previous, current) -> property(previous) != property(current) }

    @Test
    fun rotationKeepsFlingingWhenZoomIsAtZoomBoundsMin() {
        val state = state(zoomBounds = 1f..5f, initialScale = 1f)

        val frames = state.runFling(rotationVelocity = 90f, logZoomVelocity = -2f)

        frames.forEach { assertEquals(1f, it.scale) }
        assertTrue(frames.changeCount { it.angle } > 20, "angle changed on ${frames.changeCount { it.angle }} frames")
        assertTrue(state.angle > 15f, "angle was ${state.angle}")
    }

    @Test
    fun rotationKeepsFlingingWhenZoomIsAtViewportCoverMin() {
        val state = state(initialScale = 0.0001f)
        val minScale = viewportHeight / layoutBounds.height
        assertEquals(minScale, state.scale, 1e-5f)

        val frames = state.runFling(rotationVelocity = 90f, logZoomVelocity = -2f)

        assertTrue(frames.changeCount { it.angle } > 20, "angle changed on ${frames.changeCount { it.angle }} frames")
        assertTrue(state.angle > 15f, "angle was ${state.angle}")
    }

    @Test
    fun zoomKeepsFlingingWhenRotationIsAtBound() {
        val state = state(rotationBounds = -10f..10f, initialAngle = 10f, zoomBounds = 1f..5f, initialScale = 1f)

        val frames = state.runFling(rotationVelocity = 90f, logZoomVelocity = 1f)

        frames.forEach { assertEquals(10f, it.angle) }
        assertTrue(frames.changeCount { it.scale } > 20, "scale changed on ${frames.changeCount { it.scale }} frames")
        assertTrue(state.scale > 1.15f, "scale was ${state.scale}")
    }

    @Test
    fun rotationAndZoomRespectTheirOwnDecaySpecs() {
        val state = state(
            zoomBounds = 0.5f..10f,
            initialScale = 1f,
            rotationFlingDecay = FloatExponentialDecaySpec(frictionMultiplier = 10f),
            zoomFlingDecay = FloatExponentialDecaySpec(frictionMultiplier = 1f)
        )

        val frames = state.runFling(rotationVelocity = 90f, logZoomVelocity = 1f)

        val lastAngleChange = frames.lastChangeIndex { it.angle }
        val lastScaleChange = frames.lastChangeIndex { it.scale }
        assertTrue(lastAngleChange > 0)
        assertTrue(
            lastAngleChange * 2 < lastScaleChange,
            "rotation stopped at frame $lastAngleChange, zoom stopped at frame $lastScaleChange"
        )
    }

    @Test
    fun panFlingMovesAlongCollapsedBoundsAtMinScale() {
        val state = state(initialAngle = 30f, initialScale = 0.0001f, initialOffset = Offset(-10_000f, 0f))
        val segmentStart = state.offset
        state.set(offset = Offset(10_000f, 0f))
        val segmentEnd = state.offset
        state.set(offset = segmentStart)
        assertTrue(segmentEnd.x - segmentStart.x > 1_000f)

        val frames = state.runFling(panVelocity = Velocity(2_000f, 1_000f))

        assertTrue(frames.changeCount { it.offset } > 10, "offset changed on ${frames.changeCount { it.offset }} frames")
        assertTrue(state.offset.x - segmentStart.x > 300f, "offset moved from $segmentStart to ${state.offset}")
        frames.forEach {
            val distance = (it.offset - it.offset.nearestPointOnSegment(segmentStart, segmentEnd)).getDistance()
            assertTrue(distance < 0.01f, "offset ${it.offset} is outside the panning bounds")
        }
    }

    @Test
    fun panFlingIntoEdgeStopsAtTheEdge() {
        val state = state(initialScale = 1f, initialOffset = Offset(0f, 1_000f))
        assertEquals(Offset(0f, 1_000f), state.offset)

        val frames = state.runFling(panVelocity = Velocity(-3_000f, 0f))

        assertTrue(frames.size <= 4, "fling ran for ${frames.size - 1} frames")
        frames.forEach { assertEquals(Offset(0f, 1_000f), it.offset) }
    }

    @Test
    fun zeroVelocityFlingCompletesWithoutFrames() {
        val state = state(initialAngle = 20f, initialScale = 2f)
        val before = Frame(state.offset, state.angle, state.scale)

        runBlocking(BroadcastFrameClock()) {
            withTimeout(1_000) {
                state.fling(0f, 0f, Velocity.Zero, Offset(viewportWidth / 2f, viewportHeight / 2f))
            }
        }

        assertEquals(before, Frame(state.offset, state.angle, state.scale))
    }

    @Test
    fun panOnlyFlingDoesNotChangeAngleOrScale() {
        val state = state(initialAngle = 20f, initialScale = 2f)

        val frames = state.runFling(panVelocity = Velocity(2_000f, -1_500f))

        assertTrue(frames.changeCount { it.offset } > 10, "offset changed on ${frames.changeCount { it.offset }} frames")
        frames.forEach {
            assertEquals(20f, it.angle)
            assertEquals(2f, it.scale)
        }
    }

    private fun minScaleAt(angle: Float) = state(initialAngle = angle, initialScale = 0.0001f).scale

    @Test
    fun pinnedZoomFollowsDecreasingMinScaleWhileRotating() {
        val logZoomVelocity = -6f
        val state = state(initialScale = 0.0001f)
        assertEquals(minScaleAt(0f), state.scale)

        val frames = state.runFling(rotationVelocity = 90f, logZoomVelocity = logZoomVelocity)

        frames.forEach {
            assertTrue(it.scale >= minScaleAt(it.angle) * (1 - 1e-5f), "scale ${it.scale} is below the min at angle ${it.angle}")
        }
        val firstPinned = frames.indices.drop(2).first {
            abs(frames[it].scale / minScaleAt(frames[it].angle) - 1) < 1e-4f
        }
        val laterDecreases = (firstPinned + 1 until frames.size).count { frames[it].scale < frames[it - 1].scale }
        assertTrue(laterDecreases > 20, "scale decreased on $laterDecreases frames after being pinned at frame $firstPinned")
        assertTrue(state.scale < minScaleAt(0f) * 0.75f, "scale was ${state.scale}")

        val maxLogZoomPerFrame = abs(logZoomVelocity) * FRAME_NANOS / 1e9f
        frames.zipWithNext().forEach { (previous, current) ->
            val change = abs(ln(current.scale / previous.scale))
            assertTrue(change <= maxLogZoomPerFrame * 1.05f, "scale jumped by $change in one frame")
        }
    }

    @Test
    fun pinnedPanFollowsEdgeWhileZooming() {
        val state = state(initialScale = 1f, initialOffset = Offset(100_000f, 0f))
        assertEquals(layoutBounds.width - viewportWidth, state.offset.x)

        val frames = state.runFling(
            logZoomVelocity = 0.5f,
            panVelocity = Velocity(20_000f, 0f),
            centroid = Offset.Zero
        )

        val zoomEnd = frames.lastChangeIndex { it.scale }
        assertTrue(zoomEnd > 10, "zoom ran for $zoomEnd frames")
        for (i in 2..zoomEnd) {
            val edge = layoutBounds.width * frames[i].scale - viewportWidth
            assertEquals(edge, frames[i].offset.x, 0.5f, "offset left the right edge at frame $i")
            assertTrue(frames[i].offset.x > frames[i - 1].offset.x, "offset did not move right at frame $i")
        }
        assertTrue(frames.size - 1 <= zoomEnd + 3, "pan kept running ${frames.size - 1 - zoomEnd} frames after zoom ended")
    }

    @Test
    fun pinnedZoomStopsSoonAfterRotationEnds() {
        val state = state(
            initialScale = 0.0001f,
            rotationFlingDecay = FloatExponentialDecaySpec(frictionMultiplier = 10f),
            zoomFlingDecay = FloatExponentialDecaySpec(frictionMultiplier = 0.2f)
        )

        val frames = state.runFling(rotationVelocity = 90f, logZoomVelocity = -6f)

        val rotationEnd = frames.lastChangeIndex { it.angle }
        assertTrue(rotationEnd > 3, "rotation ran for $rotationEnd frames")
        assertTrue(frames.size - 1 <= rotationEnd + 3, "fling ran ${frames.size - 1 - rotationEnd} frames after rotation ended")
    }

    @Test
    fun settingStateCancelsFling() {
        val state = state(zoomBounds = 0.5f..10f, initialScale = 1f)
        var afterSet: Frame? = null
        val setFrame = 5

        val frames = state.runFling(
            rotationVelocity = 90f,
            logZoomVelocity = 1f,
            panVelocity = Velocity(2_000f, 1_000f)
        ) { index ->
            if (index == setFrame) {
                state.set(angle = 45f)
                afterSet = Frame(state.offset, state.angle, state.scale)
            }
        }

        val expected = afterSet!!
        assertEquals(45f, expected.angle)
        assertTrue(frames.size - 1 <= setFrame + 1, "fling ran ${frames.size - 1 - setFrame} frames after set")
        frames.drop(setFrame + 1).forEach { assertEquals(expected, it) }
        assertEquals(expected, Frame(state.offset, state.angle, state.scale))
    }

    @Test
    fun transformCancelsFling() {
        val state = state(zoomBounds = 0.5f..10f, initialScale = 1f)
        var afterTransform: Frame? = null
        val transformFrame = 5

        val frames = state.runFling(
            rotationVelocity = 90f,
            logZoomVelocity = 1f,
            panVelocity = Velocity(2_000f, 1_000f)
        ) { index ->
            if (index == transformFrame) {
                state.transform(
                    rotationDelta = 10f,
                    zoomFactor = 1.2f,
                    panDelta = Offset(50f, -30f),
                    centroid = Offset(viewportWidth / 2f, viewportHeight / 2f)
                )
                afterTransform = Frame(state.offset, state.angle, state.scale)
            }
        }

        val expected = afterTransform!!
        assertTrue(frames.size - 1 <= transformFrame + 1, "fling ran ${frames.size - 1 - transformFrame} frames after transform")
        frames.drop(transformFrame + 1).forEach { assertEquals(expected, it) }
        assertEquals(expected, Frame(state.offset, state.angle, state.scale))
    }

    @Test
    fun settingNothingDoesNotCancelFling() {
        val state = state(zoomBounds = 0.5f..10f, initialScale = 1f)
        val setFrame = 5

        val frames = state.runFling(rotationVelocity = 90f, logZoomVelocity = 1f) { index ->
            if (index == setFrame) state.set()
        }

        val laterFrames = frames.drop(setFrame)
        assertTrue(laterFrames.changeCount { it.angle } > 10, "angle changed on ${laterFrames.changeCount { it.angle }} frames after set()")
        assertTrue(laterFrames.changeCount { it.scale } > 10, "scale changed on ${laterFrames.changeCount { it.scale }} frames after set()")
    }

    private companion object {
        const val FRAME_NANOS = 16_666_667L
    }
}
