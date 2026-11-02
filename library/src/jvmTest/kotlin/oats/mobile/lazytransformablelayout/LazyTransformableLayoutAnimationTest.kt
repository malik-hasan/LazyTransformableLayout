package oats.mobile.lazytransformablelayout

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import oats.mobile.lazytransformablelayout.model.Pivot
import oats.mobile.lazytransformablelayout.model.TransformAnimationValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LazyTransformableLayoutAnimationTest {

    private val viewportWidth = 500
    private val viewportHeight = 700
    private val layoutBounds = DpRect(0.dp, 0.dp, 10_000.dp, 6_000.dp)
    private val linear = tween<TransformAnimationValues>(durationMillis = 300, easing = LinearEasing)

    private fun state(zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE) =
        LazyTransformableLayoutState(layoutBounds, initialZoomBounds = zoomBounds)

    private fun DpOffset.toOffset() = Offset(x.value, y.value)

    private fun LazyTransformableLayoutState.layoutAt(position: Offset) = viewportToLayout(position).toOffset()

    @Test
    fun requestAnimateToReachesTheTarget() = runComposeUiTest {
        val state = state()
        setMeasuredLayout(state, viewportWidth, viewportHeight)

        state.requestAnimateTo(
            rotationAngle = 30f,
            zoomScale = 2f,
            centerOffset = DpOffset(5_200.dp, 3_100.dp),
            animationSpec = linear
        )
        waitForIdle()

        assertEquals(30f, state.rotationAngle, 1e-3f)
        assertEquals(2f, state.zoomScale, 1e-3f)
        assertEquals(5_200f, state.centerOffset.x.value, 0.05f)
        assertEquals(3_100f, state.centerOffset.y.value, 0.05f)
    }

    @Test
    fun requestAnimateToClampsTheTarget() = runComposeUiTest {
        val state = state(zoomBounds = 0.5f..5f)
        setMeasuredLayout(state, viewportWidth, viewportHeight)

        state.requestAnimateTo(zoomScale = 1e9f, animationSpec = linear)
        waitForIdle()

        assertEquals(5f, state.zoomScale)
    }

    @Test
    fun animationInterpolatesZoomInLogSpace() = runComposeUiTest {
        val state = state()
        setMeasuredLayout(state, viewportWidth, viewportHeight)
        mainClock.autoAdvance = false

        state.requestAnimateTo(zoomScale = 4f, animationSpec = linear)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(150)

        assertTrue(state.zoomScale in 1.5f..2.7f, "scale was ${state.zoomScale}, expected near 2 halfway to 4")

        mainClock.advanceTimeBy(1_000)
        assertEquals(4f, state.zoomScale, 1e-3f)
    }

    @Test
    fun requestAnimateByKeepsTheViewportPivotFixed() = runComposeUiTest {
        val state = state()
        setMeasuredLayout(state, viewportWidth, viewportHeight)
        val pivot = Offset(100f, 200f)
        val before = state.layoutAt(pivot)

        state.requestAnimateBy(
            pivot = Pivot.Viewport(pivot),
            rotationDelta = 15f,
            zoomFactor = 2f,
            animationSpec = linear
        )
        waitForIdle()

        assertEquals(2f, state.zoomScale, 1e-3f)
        assertEquals(15f, state.rotationAngle, 1e-3f)
        val after = state.layoutAt(pivot)
        assertEquals(before.x, after.x, 0.1f)
        assertEquals(before.y, after.y, 0.1f)
    }

    @Test
    fun requestAnimateByPansByThePanDelta() = runComposeUiTest {
        val state = state()
        setMeasuredLayout(state, viewportWidth, viewportHeight)
        val position = state.centerOffset
        val before = state.layoutToViewport(position)

        state.requestAnimateBy(panDelta = Offset(40f, -20f), animationSpec = linear)
        waitForIdle()

        val after = state.layoutToViewport(position)
        assertEquals(before.x + 40f, after.x, 0.1f)
        assertEquals(before.y - 20f, after.y, 0.1f)
    }

    @Test
    fun requestsRestartFromTheCurrentState() = runComposeUiTest {
        val state = state()
        setMeasuredLayout(state, viewportWidth, viewportHeight)
        mainClock.autoAdvance = false

        state.requestAnimateBy(zoomFactor = 2f, animationSpec = linear)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(100)
        val midway = state.zoomScale
        state.requestAnimateBy(zoomFactor = 2f, animationSpec = linear)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(2_000)

        assertEquals(midway * 2f, state.zoomScale, 1e-2f)
    }

    @Test
    fun snapByBeforeTheRequestStartsDiscardsIt() = runComposeUiTest {
        val state = state()
        setMeasuredLayout(state, viewportWidth, viewportHeight)

        state.requestAnimateTo(zoomScale = 3f, animationSpec = linear)
        state.snapBy()
        waitForIdle()

        assertEquals(1f, state.zoomScale)
    }

    @Test
    fun snapToBeforeTheRequestStartsDiscardsIt() = runComposeUiTest {
        val state = state()
        setMeasuredLayout(state, viewportWidth, viewportHeight)

        state.requestAnimateBy(zoomFactor = 3f, animationSpec = linear)
        state.snapTo(rotationAngle = 10f)
        waitForIdle()

        assertEquals(1f, state.zoomScale)
        assertEquals(10f, state.rotationAngle)
    }

    @Test
    fun cancelAnimationLeavesTheStateWhereItIs() = runComposeUiTest {
        val state = state()
        setMeasuredLayout(state, viewportWidth, viewportHeight)
        mainClock.autoAdvance = false

        state.requestAnimateTo(zoomScale = 4f, animationSpec = linear)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(100)
        state.cancelAnimation()
        val canceled = state.zoomScale
        mainClock.advanceTimeBy(1_000)

        assertTrue(canceled > 1f && canceled < 4f, "scale was $canceled")
        assertEquals(canceled, state.zoomScale)
    }

    @Test
    fun snapByCancelsARunningAnimation() = runComposeUiTest {
        val state = state()
        setMeasuredLayout(state, viewportWidth, viewportHeight)
        mainClock.autoAdvance = false

        state.requestAnimateTo(rotationAngle = 90f, animationSpec = linear)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(100)
        state.snapBy(rotationDelta = 1f)
        val afterSnap = state.rotationAngle
        mainClock.advanceTimeBy(1_000)

        assertEquals(afterSnap, state.rotationAngle)
    }

    @Test
    fun preventUserInputIgnoresDefaultPriorityChangesWhileTheAnimationRuns() = runComposeUiTest {
        val state = state()
        setMeasuredLayout(state, viewportWidth, viewportHeight)
        mainClock.autoAdvance = false

        state.requestAnimateTo(
            zoomScale = 2f,
            animationSpec = linear,
            mutatePriority = MutatePriority.PreventUserInput
        )
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(100)
        state.snapTo(zoomScale = 5f)
        mainClock.advanceTimeBy(1_000)

        assertEquals(2f, state.zoomScale, 1e-3f)

        state.snapTo(zoomScale = 5f)
        assertEquals(5f, state.zoomScale, 1e-3f)
    }

    @Test
    fun defaultPriorityAnimationsAreInterruptedByDefaultPriorityChanges() = runComposeUiTest {
        val state = state()
        setMeasuredLayout(state, viewportWidth, viewportHeight)
        mainClock.autoAdvance = false

        state.requestAnimateTo(zoomScale = 2f, animationSpec = linear)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(100)
        state.snapTo(zoomScale = 5f)
        mainClock.advanceTimeBy(1_000)

        assertEquals(5f, state.zoomScale, 1e-3f)
    }

    @Test
    fun requestAnimateToBoundsFitsTheBounds() = runComposeUiTest {
        val state = state()
        setMeasuredLayout(state, viewportWidth, viewportHeight)

        state.requestAnimateToBounds(
            bounds = DpRect(1_000.dp, 1_000.dp, 1_400.dp, 1_200.dp),
            rotationAngle = 90f,
            margin = 10.dp,
            animationSpec = linear
        )
        waitForIdle()

        assertEquals(90f, state.rotationAngle, 1e-3f)
        assertEquals(1.7f, state.zoomScale, 1e-3f)
        assertEquals(1_200f, state.centerOffset.x.value, 0.05f)
        assertEquals(1_100f, state.centerOffset.y.value, 0.05f)
    }

    @Test
    fun animationClampsEachFrameToTheCurrentGeometry() = runComposeUiTest {
        val state = state()
        var startPadding by mutableStateOf(300.dp)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(viewportWidth.dp, viewportHeight.dp)) {
                    LazyTransformableLayout(
                        state = state,
                        contentPadding = PaddingValues(start = startPadding),
                        overscrollEffect = null
                    ) {}
                }
            }
        }
        waitForIdle()
        state.snapTo(centerOffset = DpOffset(2_000.dp, 3_000.dp))
        mainClock.autoAdvance = false

        state.requestAnimateTo(centerOffset = DpOffset(150.dp, 3_000.dp), animationSpec = linear)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(100)
        startPadding = 0.dp
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()

        repeat(30) {
            mainClock.advanceTimeBy(10)
            assertTrue(state.centerOffset.x.value >= 250f - 0.05f, "center ${state.centerOffset} is outside the new bounds")
        }
        mainClock.advanceTimeBy(1_000)
        assertEquals(250f, state.centerOffset.x.value, 0.05f)
    }

    private fun logZoomAfterOneFrame(
        initialScale: Float,
        start: LazyTransformableLayoutState.() -> Unit,
        retarget: LazyTransformableLayoutState.() -> Unit
    ): Pair<Float, Float> {
        var scaleBeforeRetarget = 0f
        var delta = 0f
        runComposeUiTest {
            val state = state()
            setMeasuredLayout(state, viewportWidth, viewportHeight)
            mainClock.autoAdvance = false
            state.start()
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(48)
            scaleBeforeRetarget = state.zoomScale
            state.retarget()
            mainClock.advanceTimeByFrame()
            val before = state.zoomScale
            mainClock.advanceTimeByFrame()
            delta = ln(state.zoomScale / before)
        }
        return scaleBeforeRetarget to delta
    }

    private fun restLogZoomAfterOneFrame(scale: Float, begin: LazyTransformableLayoutState.() -> Unit): Float {
        var delta = 0f
        runComposeUiTest {
            val state = state()
            setMeasuredLayout(state, viewportWidth, viewportHeight)
            state.snapTo(zoomScale = scale)
            mainClock.autoAdvance = false
            state.begin()
            mainClock.advanceTimeByFrame()
            val before = state.zoomScale
            mainClock.advanceTimeByFrame()
            delta = ln(state.zoomScale / before)
        }
        return delta
    }

    @Test
    fun retargetedAnimateByContinuesTheRunningVelocity() {
        val (scale, continued) = logZoomAfterOneFrame(
            initialScale = 1f,
            start = { requestAnimateBy(zoomFactor = 2f) },
            retarget = { requestAnimateBy(zoomFactor = 2f) }
        )
        val fromRest = restLogZoomAfterOneFrame(scale) { requestAnimateBy(zoomFactor = 2f) }

        assertTrue(fromRest > 0f)
        assertTrue(continued > fromRest * 1.5f, "continued $continued per frame, from rest $fromRest")
    }

    @Test
    fun retargetedAnimateToContinuesTheRunningVelocity() {
        val (scale, continued) = logZoomAfterOneFrame(
            initialScale = 1f,
            start = { requestAnimateTo(zoomScale = 2f) },
            retarget = { requestAnimateTo(zoomScale = 4f) }
        )
        val fromRest = restLogZoomAfterOneFrame(scale) { requestAnimateTo(zoomScale = 4f) }

        assertTrue(fromRest > 0f)
        assertTrue(continued > fromRest * 1.5f, "continued $continued per frame, from rest $fromRest")
    }

    @Test
    fun animateByMovesTheContentLikeSnapByForTheSameZoomFactorAndPanDelta() {
        for (pivot in listOf(Pivot.ContentCenter, Pivot.Viewport(Offset(100f, 200f)))) {
            val snapped = state().measured(viewportWidth, viewportHeight)
            snapped.snapBy(pivot = pivot, zoomFactor = 2f, panDelta = Offset(60f, -40f))

            runComposeUiTest {
                val animated = state()
                setMeasuredLayout(animated, viewportWidth, viewportHeight)
                animated.requestAnimateBy(
                    pivot = pivot,
                    zoomFactor = 2f,
                    panDelta = Offset(60f, -40f),
                    animationSpec = linear
                )
                waitForIdle()

                assertEquals(snapped.zoomScale, animated.zoomScale, 1e-3f)
                assertEquals(snapped.centerOffset.x.value, animated.centerOffset.x.value, 0.1f)
                assertEquals(snapped.centerOffset.y.value, animated.centerOffset.y.value, 0.1f)
                assertTrue(snapped.centerOffset.x.value != 5_000f, "snapBy did not pan")
            }
        }
    }

    private fun ComposeUiTest.launchedIn(
        state: LazyTransformableLayoutState,
        block: suspend () -> Unit
    ) {
        lateinit var scope: CoroutineScope
        setMeasuredLayout(state, viewportWidth, viewportHeight, onScope = { scope = it })
        mainClock.autoAdvance = false
        scope.launch { block() }
    }

    @Test
    fun animateToClampsRotationAndZoomToTheirBounds() = runComposeUiTest {
        val state = LazyTransformableLayoutState(
            layoutBounds,
            initialRotationBounds = -10f..10f,
            initialZoomBounds = 0.5f..4f
        )
        launchedIn(state) {
            state.animateTo(rotationAngle = 500f, zoomScale = 100f, animationSpec = linear)
        }
        mainClock.advanceTimeBy(2_000)

        assertEquals(10f, state.rotationAngle, 1e-3f)
        assertEquals(4f, state.zoomScale, 1e-3f)
    }

    @Test
    fun animateToClampsTheCenter() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        launchedIn(state) {
            state.animateTo(centerOffset = DpOffset(-100.dp, 3_000.dp), animationSpec = linear)
        }
        mainClock.advanceTimeBy(2_000)

        assertEquals(250f, state.centerOffset.x.value, 0.1f)
    }

    @Test
    fun snapToBoundsClampsTheFitToTheBounds() {
        val tooLarge = state().measured(viewportWidth, viewportHeight)
        tooLarge.snapToBounds(layoutBounds)
        assertEquals(700f / 6_000f, tooLarge.zoomScale, 1e-5f)

        val rotated = LazyTransformableLayoutState(layoutBounds, initialRotationBounds = -10f..10f)
            .measured(viewportWidth, viewportHeight)
        rotated.snapToBounds(layoutBounds, rotationAngle = 500f)
        assertEquals(10f, rotated.rotationAngle)
    }

    @Test
    fun animateToStartsFromTheClampedValuesWhenCalledBeforeTheFirstLayout() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds, initialCenterOffset = DpOffset(-500.dp, 99_999.dp))
        setContent {
            LaunchedEffect(Unit) { state.animateTo(zoomScale = 2f, animationSpec = linear) }
            Box(Modifier.size(viewportWidth.dp, viewportHeight.dp)) {
                LazyTransformableLayout(state, overscrollEffect = null) {}
            }
        }
        mainClock.advanceTimeBy(2_000)

        assertEquals(2f, state.zoomScale, 1e-3f)
        assertEquals(250f, state.centerOffset.x.value, 0.5f)
        assertEquals(5_650f, state.centerOffset.y.value, 0.5f)
    }

    @Test
    fun cancelAnimationDoesNotCancelTheCaller() = runComposeUiTest {
        val state = state()
        var reachedAfter = false
        launchedIn(state) {
            state.animateTo(zoomScale = 4f, animationSpec = linear)
            reachedAfter = true
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(100)
        state.cancelAnimation()
        mainClock.advanceTimeBy(1_000)

        assertTrue(reachedAfter, "the caller was canceled")
        assertTrue(state.zoomScale > 1f && state.zoomScale < 4f, "scale was ${state.zoomScale}")
    }

    @Test
    fun aZeroVelocityFlingDoesNotCancelARunningAnimation() = runComposeUiTest {
        val state = state()
        lateinit var scope: CoroutineScope
        setMeasuredLayout(state, viewportWidth, viewportHeight, onScope = { scope = it })
        mainClock.autoAdvance = false
        scope.launch { state.animateTo(zoomScale = 4f, animationSpec = linear) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(100)

        scope.launch { state.fling(initialPanVelocity = -Velocity.Zero) }
        scope.launch { state.fling(initialPanVelocity = Velocity.Zero) }
        mainClock.advanceTimeBy(1_000)

        assertEquals(4f, state.zoomScale, 1e-3f)
    }

    @Test
    fun snapByDoesNotCancelTheCaller() = runComposeUiTest {
        val state = state()
        var reachedAfter = false
        launchedIn(state) {
            state.animateBy(rotationDelta = 90f, animationSpec = linear)
            reachedAfter = true
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(100)
        state.snapBy(rotationDelta = 1f)
        mainClock.advanceTimeBy(1_000)

        assertTrue(reachedAfter, "the caller was canceled")
    }

    @Test
    fun aCanceledAnimateToDoesNotApplyItsTarget() = runComposeUiTest {
        val state = state(zoomBounds = 0.5f..4f)
        launchedIn(state) {
            state.animateTo(zoomScale = 100f, animationSpec = linear)
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(100)
        state.cancelAnimation()
        val canceledAt = state.zoomScale
        mainClock.advanceTimeBy(1_000)

        assertEquals(canceledAt, state.zoomScale)
        assertTrue(state.zoomScale < 4f, "scale was ${state.zoomScale}")
    }

    @Test
    fun aCompletedAnimateToSnapsToTheClampedTarget() = runComposeUiTest {
        val state = state()
        launchedIn(state) {
            state.animateTo(rotationAngle = 30f, zoomScale = 2f, animationSpec = linear)
        }
        mainClock.advanceTimeBy(2_000)

        assertEquals(30f, state.rotationAngle)
        assertEquals(2f, state.zoomScale)
    }

    private fun ComposeUiTest.advanceFrames(count: Int, onFrame: () -> Unit = {}) {
        repeat(count) {
            mainClock.advanceTimeByFrame()
            onFrame()
        }
    }

    @Test
    fun loweringZoomMaxMidAnimationClampsEveryFrameAndEndsAtTheNewMax() = runComposeUiTest {
        val state = state(zoomBounds = 0.5f..8f)
        var completed = false
        launchedIn(state) {
            state.animateTo(zoomScale = 8f, animationSpec = linear)
            completed = true
        }
        advanceFrames(8)
        assertTrue(state.zoomScale in 1.1f..8f, "scale was ${state.zoomScale}")

        state.updateBounds(zoomBounds = 0.5f..3f)
        assertFalse(completed, "updateBounds completed the animation")
        val seen = mutableListOf<Float>()
        advanceFrames(40) { seen += state.zoomScale }

        assertTrue(seen.any { it > 1.1f }, "the animation stopped progressing")
        seen.forEach { assertTrue(it <= 3f + 1e-4f, "scale was $it") }
        assertTrue(completed, "the animation did not finish")
        assertEquals(3f, state.zoomScale, 1e-3f)
    }

    @Test
    fun loweringZoomMaxMidRequestedAnimationEndsAtTheNewMax() = runComposeUiTest {
        val state = state(zoomBounds = 0.5f..8f)
        setMeasuredLayout(state, viewportWidth, viewportHeight)
        mainClock.autoAdvance = false

        state.requestAnimateTo(zoomScale = 8f, animationSpec = linear)
        advanceFrames(8)
        state.updateBounds(zoomBounds = 0.5f..3f)
        val seen = mutableListOf<Float>()
        advanceFrames(40) { seen += state.zoomScale }

        seen.forEach { assertTrue(it <= 3f + 1e-4f, "scale was $it") }
        assertEquals(3f, state.zoomScale, 1e-3f)
    }

    @Test
    fun loweringRotationMaxMidAnimationClampsEveryFrameAndEndsAtTheNewMax() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds, initialRotationBounds = -90f..90f)
        var completed = false
        launchedIn(state) {
            state.animateTo(rotationAngle = 90f, animationSpec = linear)
            completed = true
        }
        advanceFrames(8)
        assertTrue(state.rotationAngle > 20f, "angle was ${state.rotationAngle}")

        state.updateBounds(rotationBounds = -90f..15f)
        assertFalse(completed, "updateBounds completed the animation")
        val seen = mutableListOf<Float>()
        advanceFrames(40) { seen += state.rotationAngle }

        seen.forEach { assertTrue(it <= 15f + 1e-4f, "angle was $it") }
        assertTrue(completed, "the animation did not finish")
        assertEquals(15f, state.rotationAngle, 1e-3f)
    }

    @Test
    fun shrinkingLayoutBoundsMidAnimationKeepsTheCenterInsideAndEndsClamped() = runComposeUiTest {
        val state = state()
        var completed = false
        launchedIn(state) {
            state.animateTo(centerOffset = DpOffset(9_000.dp, 3_000.dp), animationSpec = linear)
            completed = true
        }
        advanceFrames(8)
        assertTrue(state.centerOffset.x.value > 1_800f, "x was ${state.centerOffset.x.value}")

        state.updateBounds(layoutBounds = DpRect(0.dp, 0.dp, 2_000.dp, 6_000.dp))
        assertFalse(completed, "updateBounds completed the animation")
        val seen = mutableListOf<Float>()
        advanceFrames(40) { seen += state.centerOffset.x.value }

        seen.forEach { assertTrue(it <= 1_750f + 0.1f, "x was $it") }
        assertTrue(completed, "the animation did not finish")
        assertEquals(1_750f, state.centerOffset.x.value, 0.1f)
        assertEquals(3_000f, state.centerOffset.y.value, 0.1f)
    }

    @Test
    fun updateBoundsWithoutArgumentsLeavesARunningAnimationUntouched() = runComposeUiTest {
        val state = state()
        var completed = false
        launchedIn(state) {
            state.animateTo(zoomScale = 4f, rotationAngle = 30f, animationSpec = linear)
            completed = true
        }
        advanceFrames(8)
        val scale = state.zoomScale
        val angle = state.rotationAngle

        state.updateBounds()
        assertEquals(scale, state.zoomScale)
        assertEquals(angle, state.rotationAngle)
        assertFalse(completed)
        advanceFrames(40)

        assertTrue(completed, "the animation did not finish")
        assertEquals(4f, state.zoomScale, 1e-3f)
        assertEquals(30f, state.rotationAngle, 1e-3f)
    }
}
