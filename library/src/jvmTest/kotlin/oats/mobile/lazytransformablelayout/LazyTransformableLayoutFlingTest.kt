package oats.mobile.lazytransformablelayout

import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import oats.mobile.lazytransformablelayout.extension.nearestPointOnSegment
import oats.mobile.lazytransformablelayout.extension.toLayoutAxes
import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LazyTransformableLayoutFlingTest {

    private data class Frame(val offset: Offset, val angle: Float, val scale: Float)

    private val viewportWidth = 500
    private val viewportHeight = 700
    private val layoutBounds = DpRect(0.dp, 0.dp, 10_000.dp, 6_000.dp)
    private val layoutWidth = 10_000f
    private val layoutHeight = 6_000f

    private fun state(
        rotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
        zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
        initialOffset: Offset = Offset(3_000f, 2_000f),
        initialAngle: Float = 0f,
        initialScale: Float = 1f,
        rotationFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec(),
        zoomFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec()
    ) = LazyTransformableLayoutState(
        initialLayoutBounds = layoutBounds,
        initialRotationBounds = rotationBounds,
        initialZoomBounds = zoomBounds,
        initialRotationAngle = initialAngle,
        initialZoomScale = initialScale,
        rotationFlingDecay = rotationFlingDecay,
        zoomFlingDecay = zoomFlingDecay
    ).measured(viewportWidth, viewportHeight).apply {
        snapToTranslation(initialOffset)
    }

    private fun LazyTransformableLayoutState.snapToTranslation(translation: Offset) {
        val center = (translation + Offset(viewportWidth / 2f, viewportHeight / 2f)).toLayoutAxes(zoomScale, rotationAngle)
        snapTo(centerOffset = DpOffset(center.x.dp, center.y.dp))
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
        val frames = mutableListOf(Frame(translation, rotationAngle, zoomScale))
        val job = launch(clock) {
            fling(rotationVelocity, logZoomVelocity, panVelocity, Pivot.Viewport(centroid))
        }
        var frameTimeNanos = 0L
        while (job.isActive && frames.size <= maxFrames) {
            yield()
            if (clock.hasAwaiters) {
                frameTimeNanos += FRAME_NANOS
                clock.sendFrame(frameTimeNanos)
                frames += Frame(translation, rotationAngle, zoomScale)
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
    fun `given zoom at the zoom bounds min, when flinging, should keep rotating`() {
        val state = state(zoomBounds = 1f..5f, initialScale = 1f)

        val frames = state.runFling(rotationVelocity = 90f, logZoomVelocity = -2f)

        frames.forEach { assertEquals(1f, it.scale) }
        assertTrue(frames.changeCount { it.angle } > 20, "angle changed on ${frames.changeCount { it.angle }} frames")
        assertTrue(state.rotationAngle > 15f, "angle was ${state.rotationAngle}")
    }

    @Test
    fun `given zoom at the viewport cover min, when flinging, should keep rotating`() {
        val state = state(initialScale = 0.0001f)
        val minScale = viewportHeight / layoutHeight
        assertEquals(minScale, state.zoomScale, 1e-5f)

        val frames = state.runFling(rotationVelocity = 90f, logZoomVelocity = -2f)

        assertTrue(frames.changeCount { it.angle } > 20, "angle changed on ${frames.changeCount { it.angle }} frames")
        assertTrue(state.rotationAngle > 15f, "angle was ${state.rotationAngle}")
    }

    @Test
    fun `given rotation at its bound, when flinging, should keep zooming`() {
        val state = state(rotationBounds = -10f..10f, initialAngle = 10f, zoomBounds = 1f..5f, initialScale = 1f)

        val frames = state.runFling(rotationVelocity = 90f, logZoomVelocity = 1f)

        frames.forEach { assertEquals(10f, it.angle) }
        assertTrue(frames.changeCount { it.scale } > 20, "scale changed on ${frames.changeCount { it.scale }} frames")
        assertTrue(state.zoomScale > 1.15f, "scale was ${state.zoomScale}")
    }

    @Test
    fun `given rotation and zoom flings, should respect their own decay specs`() {
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
    fun `given collapsed panning bounds at min scale, when flinging pan, should move along the collapsed bounds`() {
        val state = state(initialAngle = 30f, initialScale = 0.0001f, initialOffset = Offset(-10_000f, 0f))
        val segmentStart = state.translation
        state.snapToTranslation(Offset(10_000f, 0f))
        val segmentEnd = state.translation
        state.snapToTranslation(segmentStart)
        assertTrue(segmentEnd.x - segmentStart.x > 250f)

        val frames = state.runFling(panVelocity = Velocity(2_000f, 1_000f))

        assertTrue(frames.changeCount { it.offset } > 10, "offset changed on ${frames.changeCount { it.offset }} frames")
        assertTrue(state.translation.x - segmentStart.x > 100f, "offset moved from $segmentStart to ${state.translation}")
        frames.forEach {
            val distance = (it.offset - it.offset.nearestPointOnSegment(segmentStart, segmentEnd)).getDistance()
            assertTrue(distance < 0.01f, "offset ${it.offset} is outside the panning bounds")
        }
    }

    @Test
    fun `given a pan fling toward an edge, should stop at the edge`() {
        val state = state(initialScale = 1f, initialOffset = Offset(0f, 1_000f))
        assertEquals(0f, state.translation.x, 0.01f)
        assertEquals(1_000f, state.translation.y, 0.01f)

        val frames = state.runFling(panVelocity = Velocity(-3_000f, 0f))

        assertTrue(frames.size <= 4, "fling ran for ${frames.size - 1} frames")
        frames.forEach { assertEquals(Offset(0f, 1_000f), it.offset) }
    }

    @Test
    fun `given zero velocity, when flinging, should complete without frames`() {
        val state = state(initialAngle = 20f, initialScale = 2f)
        val before = Frame(state.translation, state.rotationAngle, state.zoomScale)

        runBlocking(BroadcastFrameClock()) {
            withTimeout(1_000) {
                state.fling(0f, 0f, Velocity.Zero, Pivot.Viewport(Offset(viewportWidth / 2f, viewportHeight / 2f)))
            }
        }

        assertEquals(before, Frame(state.translation, state.rotationAngle, state.zoomScale))
    }

    @Test
    fun `given a pan only fling, should not change angle or scale`() {
        val state = state(initialAngle = 20f, initialScale = 2f)

        val frames = state.runFling(panVelocity = Velocity(2_000f, -1_500f))

        assertTrue(frames.changeCount { it.offset } > 10, "offset changed on ${frames.changeCount { it.offset }} frames")
        frames.forEach {
            assertEquals(20f, it.angle)
            assertEquals(2f, it.scale)
        }
    }

    @Test
    fun `given a horizontal pan fling, should only move horizontally`() {
        val state = state()
        val before = state.translation

        val frames = state.runFling(panVelocity = Velocity(2_000f, 0f))

        assertTrue(frames.changeCount { it.offset } > 10)
        assertTrue(state.translation.x - before.x > 100f, "moved from $before to ${state.translation}")
        frames.forEach { assertEquals(before.y, it.offset.y) }
    }

    @Test
    fun `given a vertical pan fling, should only move vertically`() {
        val state = state()
        val before = state.translation

        val frames = state.runFling(panVelocity = Velocity(0f, -2_000f))

        assertTrue(frames.changeCount { it.offset } > 10)
        assertTrue(before.y - state.translation.y > 100f, "moved from $before to ${state.translation}")
        frames.forEach { assertEquals(before.x, it.offset.x) }
    }

    private fun minScaleAt(angle: Float) = state(initialAngle = angle, initialScale = 0.0001f).zoomScale

    @Test
    fun `given a zoom pinned to the min scale, when rotating, should follow the decreasing min scale`() {
        val logZoomVelocity = -6f
        val state = state(initialScale = 0.0001f)
        assertEquals(minScaleAt(0f), state.zoomScale)

        val frames = state.runFling(rotationVelocity = 90f, logZoomVelocity = logZoomVelocity)

        frames.forEach {
            assertTrue(it.scale >= minScaleAt(it.angle) * (1 - 1e-5f), "scale ${it.scale} is below the min at angle ${it.angle}")
        }
        val firstPinned = frames.indices.drop(2).first {
            abs(frames[it].scale / minScaleAt(frames[it].angle) - 1) < 1e-4f
        }
        val laterDecreases = (firstPinned + 1 until frames.size).count { frames[it].scale < frames[it - 1].scale }
        assertTrue(laterDecreases > 20, "scale decreased on $laterDecreases frames after being pinned at frame $firstPinned")
        assertTrue(state.zoomScale < minScaleAt(0f) * 0.75f, "scale was ${state.zoomScale}")

        val maxLogZoomPerFrame = abs(logZoomVelocity) * FRAME_NANOS / 1e9f
        frames.zipWithNext().forEach { (previous, current) ->
            val change = abs(ln(current.scale / previous.scale))
            assertTrue(change <= maxLogZoomPerFrame * 1.05f, "scale jumped by $change in one frame")
        }
    }

    @Test
    fun `given a pan pinned to an edge, when zooming, should follow the edge`() {
        val state = state(initialScale = 1f, initialOffset = Offset(100_000f, 0f))
        assertEquals(layoutWidth - viewportWidth, state.translation.x, 0.01f)

        val frames = state.runFling(
            logZoomVelocity = 0.5f,
            panVelocity = Velocity(20_000f, 0f),
            centroid = Offset.Zero
        )

        val zoomEnd = frames.lastChangeIndex { it.scale }
        assertTrue(zoomEnd > 10, "zoom ran for $zoomEnd frames")
        for (i in 2..zoomEnd) {
            val edge = layoutWidth * frames[i].scale - viewportWidth
            assertEquals(edge, frames[i].offset.x, 0.5f, "offset left the right edge at frame $i")
            assertTrue(frames[i].offset.x > frames[i - 1].offset.x, "offset did not move right at frame $i")
        }
        assertTrue(frames.size - 1 <= zoomEnd + 3, "pan kept running ${frames.size - 1 - zoomEnd} frames after zoom ended")
    }

    @Test
    fun `given a zoom pinned to the min scale, when rotation ends, should stop soon after`() {
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
    fun `given a running fling, when setting the state, should cancel the fling`() {
        val state = state(zoomBounds = 0.5f..10f, initialScale = 1f)
        var afterSet: Frame? = null
        val setFrame = 5

        val frames = state.runFling(
            rotationVelocity = 90f,
            logZoomVelocity = 1f,
            panVelocity = Velocity(2_000f, 1_000f)
        ) { index ->
            if (index == setFrame) {
                state.snapTo(rotationAngle = 45f)
                afterSet = Frame(state.translation, state.rotationAngle, state.zoomScale)
            }
        }

        val expected = afterSet!!
        assertEquals(45f, expected.angle)
        assertTrue(frames.size - 1 <= setFrame + 1, "fling ran ${frames.size - 1 - setFrame} frames after set")
        frames.drop(setFrame + 1).forEach { assertEquals(expected, it) }
        assertEquals(expected, Frame(state.translation, state.rotationAngle, state.zoomScale))
    }

    @Test
    fun `given a running fling, when transforming, should cancel the fling`() {
        val state = state(zoomBounds = 0.5f..10f, initialScale = 1f)
        var afterTransform: Frame? = null
        val transformFrame = 5

        val frames = state.runFling(
            rotationVelocity = 90f,
            logZoomVelocity = 1f,
            panVelocity = Velocity(2_000f, 1_000f)
        ) { index ->
            if (index == transformFrame) {
                state.snapBy(
                    pivot = Pivot.Viewport(Offset(viewportWidth / 2f, viewportHeight / 2f)),
                    rotationDelta = 10f,
                    zoomFactor = 1.2f,
                    panDelta = Offset(50f, -30f)
                )
                afterTransform = Frame(state.translation, state.rotationAngle, state.zoomScale)
            }
        }

        val expected = afterTransform!!
        assertTrue(frames.size - 1 <= transformFrame + 1, "fling ran ${frames.size - 1 - transformFrame} frames after transform")
        frames.drop(transformFrame + 1).forEach { assertEquals(expected, it) }
        assertEquals(expected, Frame(state.translation, state.rotationAngle, state.zoomScale))
    }

    @Test
    fun `given a running fling, when setting nothing, should still cancel the fling`() {
        val state = state(zoomBounds = 0.5f..10f, initialScale = 1f)
        val setFrame = 5

        val frames = state.runFling(rotationVelocity = 90f, logZoomVelocity = 1f) { index ->
            if (index == setFrame) state.snapTo()
        }

        assertTrue(frames.size - 1 <= setFrame + 1, "fling ran ${frames.size - 1 - setFrame} frames after snapTo()")
    }

    @Test
    fun `given a running fling, when updating bounds, should not cancel it and keep the center in the new layout bounds`() {
        val state = state()
        val newBounds = DpRect(2_000.dp, 1_000.dp, 3_800.dp, 2_850.dp)
        val setFrame = 5

        val frames = state.runFling(panVelocity = Velocity(3_000f, 2_000f)) { index ->
            if (index == setFrame) state.updateBounds(layoutBounds = newBounds)
            if (index >= setFrame) {
                val center = state.centerOffset
                assertTrue(center.x.value in 2_250f..3_550f, "x was ${center.x.value} at frame $index")
                assertTrue(center.y.value in 1_350f..2_500f, "y was ${center.y.value} at frame $index")
            }
        }

        assertTrue(frames.size - 1 > setFrame + 3, "fling ran ${frames.size - 1 - setFrame} frames after updateBounds")
        assertTrue(frames.drop(setFrame + 1).changeCount { it.offset } > 0, "the fling stopped moving")
    }

    private companion object {
        const val FRAME_NANOS = 16_666_667L
    }

    @Test
    fun `given an unspecified centroid, when flinging rotation and zoom, should keep the content center fixed on screen without NaN`() {
        val state = state()
        val contentCenter = Offset(viewportWidth / 2f, viewportHeight / 2f)
        val layoutPoint = state.viewportToLayout(contentCenter)

        val frames = state.runFling(
            rotationVelocity = 90f,
            logZoomVelocity = 1f,
            centroid = Offset.Unspecified
        )

        assertTrue(frames.changeCount { it.angle } > 10)
        assertTrue(frames.changeCount { it.scale } > 10)
        frames.forEach { assertTrue(it.angle.isFinite() && it.scale.isFinite() && it.offset.x.isFinite()) }
        val after = state.layoutToViewport(layoutPoint)
        assertEquals(contentCenter.x, after.x, 0.5f)
        assertEquals(contentCenter.y, after.y, 0.5f)
    }

    @Test
    fun `given a viewport centroid, when flinging rotation and zoom, should keep the point under it fixed on screen`() {
        val state = state()
        val centroid = Offset(100f, 200f)
        val layoutPoint = state.viewportToLayout(centroid)

        state.runFling(rotationVelocity = 90f, logZoomVelocity = 1f, centroid = centroid)

        val after = state.layoutToViewport(layoutPoint)
        assertEquals(centroid.x, after.x, 0.5f)
        assertEquals(centroid.y, after.y, 0.5f)
    }
}
