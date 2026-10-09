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
        maxFrames: Int = 2_000
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

    private companion object {
        const val FRAME_NANOS = 16_666_667L
    }
}
