package mobile.oats.lazytransformablelayout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalPlatformPrefetchScheduler
import androidx.compose.ui.platform.PlatformPrefetchRequest
import androidx.compose.ui.platform.PlatformPrefetchRequestScope
import androidx.compose.ui.platform.PlatformPrefetchScheduler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mobile.oats.lazytransformablelayout.extension.toLayoutAxes
import mobile.oats.lazytransformablelayout.internal.Parallelogram
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LazyTransformableLayoutFlingPathTest {

    private val viewportWidth = 500
    private val viewportHeight = 700
    private val viewportCenter = Offset(viewportWidth / 2f, viewportHeight / 2f)
    private val layoutWidth = 10_000f
    private val layoutHeight = 6_000f

    private fun state(
        zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
        initialOffset: Offset = Offset(3_000f, 2_000f)
    ) = LazyTransformableLayoutState(
        initialLayoutBounds = DpRect(0.dp, 0.dp, layoutWidth.dp, layoutHeight.dp),
        initialZoomBounds = zoomBounds
    ).measured(viewportWidth, viewportHeight).apply {
        val center = (initialOffset + viewportCenter).toLayoutAxes(zoomScale, rotationAngle)
        snapTo(centerOffset = DpOffset(center.x.dp, center.y.dp))
    }

    private fun LazyTransformableLayoutState.runFling(
        logZoomVelocity: Float = 0f,
        panVelocity: Velocity = Velocity.Zero,
        onFrame: (index: Int) -> Unit = {}
    ): List<Pair<Float, Parallelogram>>? = runBlocking {
        val clock = BroadcastFrameClock()
        val job = launch(clock, start = CoroutineStart.UNDISPATCHED) {
            fling(0f, logZoomVelocity, panVelocity, Pivot.Viewport(viewportCenter))
        }
        val path = flingPath
        var frameTimeNanos = 0L
        var frame = 0
        while (job.isActive && frame < 2_000) {
            yield()
            if (clock.hasAwaiters) {
                frameTimeNanos += FRAME_NANOS
                clock.sendFrame(frameTimeNanos)
                onFrame(++frame)
            }
        }
        job.cancelAndJoin()
        path
    }

    private fun LazyTransformableLayoutState.viewportCornersInLayout() = listOf(
        Offset.Zero,
        Offset(viewportWidth.toFloat(), 0f),
        Offset(viewportWidth.toFloat(), viewportHeight.toFloat()),
        Offset(0f, viewportHeight.toFloat())
    ).map { viewportToLayout(it).run { Offset(x.value, y.value) } }

    private fun List<Pair<Float, Parallelogram>>.covers(point: Offset) =
        any { (_, bounds) -> Parallelogram(point, point, point, point) in bounds }

    @Test
    fun `given a pan fling, should cover the start and end viewports with the path`() {
        val state = state()
        val startCorners = state.viewportCornersInLayout()

        val path = assertNotNull(state.runFling(panVelocity = Velocity(3_000f, -2_000f)))

        val endCorners = state.viewportCornersInLayout()
        assertTrue(path.size > 1, "path had ${path.size} entries")
        assertTrue((endCorners[0] - startCorners[0]).getDistance() > 500f, "fling moved from ${startCorners[0]} to ${endCorners[0]}")
        (startCorners + endCorners).forEach { assertTrue(path.covers(it), "path does not cover $it") }
    }

    @Test
    fun `given a pan fling toward a layout bound, should keep the path within the reachable region`() {
        val state = state(initialOffset = Offset(layoutWidth - viewportWidth - 200f, 1_000f))

        val path = assertNotNull(state.runFling(panVelocity = Velocity(20_000f, 0f)))

        assertEquals(layoutWidth - viewportWidth, state.translation.x, 0.5f)
        val maxRight = path.maxOf { (_, bounds) -> bounds.axisAlignedBoundingBox.right }
        assertTrue(maxRight >= layoutWidth, "path reached $maxRight")
        assertTrue(maxRight <= layoutWidth + 256f + 0.5f, "path extended to $maxRight")
    }

    @Test
    fun `given a zoom out fling, should end the path with a larger min item dimension and larger bounds`() {
        val state = state(zoomBounds = 0.1f..10f)

        val path = assertNotNull(state.runFling(logZoomVelocity = -2f))

        val (firstMinItemDimension, firstBounds) = path.first()
        val (lastMinItemDimension, lastBounds) = path.last()
        assertTrue(lastMinItemDimension > firstMinItemDimension, "min item dimension went from $firstMinItemDimension to $lastMinItemDimension")
        assertTrue(firstBounds in lastBounds, "last bounds $lastBounds do not contain first bounds $firstBounds")
        val firstArea = firstBounds.axisAlignedBoundingBox.size.run { width * height }
        val lastArea = lastBounds.axisAlignedBoundingBox.size.run { width * height }
        assertTrue(lastArea > firstArea, "bounds area went from $firstArea to $lastArea")
    }

    @Test
    fun `given zero velocity, should not set a fling path`() {
        val state = state()

        val path = state.runFling()

        assertNull(path)
        assertNull(state.flingPath)
    }

    @Test
    fun `given a fling, when it finishes, should clear the fling path`() {
        val state = state()

        val path = state.runFling(panVelocity = Velocity(2_000f, 0f))

        assertNotNull(path)
        assertNull(state.flingPath)
    }

    @Test
    fun `given a fling, when it is cancelled, should clear the fling path`() {
        val state = state()
        var pathBeforeCancel: List<Pair<Float, Parallelogram>>? = null

        state.runFling(panVelocity = Velocity(2_000f, 0f)) { frame ->
            if (frame == 3) {
                pathBeforeCancel = state.flingPath
                state.cancelAnimation()
            }
        }

        assertNotNull(pathBeforeCancel)
        assertNull(state.flingPath)
    }

    private companion object {
        const val FRAME_NANOS = 16_666_667L
    }
}

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class LazyTransformableLayoutFlingPrefetchTest {

    private class TestPrefetchScheduler : PlatformPrefetchScheduler {
        private val requests = ArrayDeque<PlatformPrefetchRequest>()

        override fun scheduleHighPriorityPrefetch(request: PlatformPrefetchRequest) {
            requests.addLast(request)
        }

        override fun scheduleLowPriorityPrefetch(request: PlatformPrefetchRequest) {
            requests.addLast(request)
        }

        fun runAll() {
            val scope = object : PlatformPrefetchRequestScope {
                override fun availableTimeNanos() = Long.MAX_VALUE
            }
            var executions = 0
            while (requests.isNotEmpty() && executions++ < 10_000) {
                val request = requests.removeFirst()
                if (with(request) { scope.execute() }) requests.addLast(request)
            }
        }
    }

    private val itemSize = 100f
    private val itemSpacing = 200f
    private val itemCount = 100
    private val itemTop = 200f

    private fun itemLeft(index: Int) = index * itemSpacing

    private fun LazyTransformableLayoutState.inCompositionBounds(index: Int) =
        compositionBounds!!.second.intersects(itemLeft(index), itemTop, itemLeft(index) + itemSize, itemTop + itemSize)

    private fun ComposeUiTest.setRowLayout(
        state: LazyTransformableLayoutState,
        scheduler: TestPrefetchScheduler,
        composed: MutableSet<Int>,
        onScope: (CoroutineScope) -> Unit
    ) {
        setContent {
            onScope(rememberCoroutineScope())
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalPlatformPrefetchScheduler provides scheduler
            ) {
                Box(Modifier.size(500.dp)) {
                    LazyTransformableLayout(state = state, overscrollEffect = null) {
                        repeat(itemCount) { index ->
                            item(
                                offset = DpOffset(itemLeft(index).dp, itemTop.dp),
                                size = DpSize(itemSize.dp, itemSize.dp),
                                key = index
                            ) {
                                DisposableEffect(Unit) {
                                    composed += index
                                    onDispose { composed -= index }
                                }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun rowState() = LazyTransformableLayoutState(
        initialLayoutBounds = DpRect(0.dp, 0.dp, (itemCount * itemSpacing).dp, 500.dp),
        initialCenterOffset = DpOffset(250.dp, 250.dp)
    )

    @Test
    fun `given a pan fling, should precompose items along its path before they reach the composition bounds`() = runComposeUiTest {
        val state = rowState()
        val scheduler = TestPrefetchScheduler()
        val composed = mutableSetOf<Int>()
        lateinit var scope: CoroutineScope
        setRowLayout(state, scheduler, composed) { scope = it }
        assertEquals(setOf(0, 1, 2, 3), composed)

        mainClock.autoAdvance = false
        scope.launch { state.fling(initialPanVelocity = Velocity(20_000f, 0f)) }
        mainClock.advanceTimeByFrame()
        runOnUiThread { scheduler.runAll() }

        val alongPath = 15
        val beyondPath = 40
        assertFalse(state.inCompositionBounds(alongPath), "item $alongPath is already in the composition bounds")
        assertTrue(alongPath in composed, "item $alongPath was not precomposed, composed: ${composed.sorted()}")
        assertFalse(beyondPath in composed, "item $beyondPath past the fling end was precomposed")
    }

    @Test
    fun `given precomposed items along a fling path, when cancelling the fling, should dispose the ones off screen`() = runComposeUiTest {
        val state = rowState()
        val scheduler = TestPrefetchScheduler()
        val composed = mutableSetOf<Int>()
        lateinit var scope: CoroutineScope
        setRowLayout(state, scheduler, composed) { scope = it }

        mainClock.autoAdvance = false
        scope.launch { state.fling(initialPanVelocity = Velocity(20_000f, 0f)) }
        mainClock.advanceTimeByFrame()
        runOnUiThread { scheduler.runAll() }
        assertTrue(15 in composed, "item 15 was not precomposed, composed: ${composed.sorted()}")

        state.cancelAnimation()
        mainClock.advanceTimeByFrame()
        waitForIdle()

        assertNull(state.flingPath)
        val inBounds = (0 until itemCount).filter { state.inCompositionBounds(it) }.toSet()
        assertEquals(inBounds, composed.toSet())
    }
}
