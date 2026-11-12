package mobile.oats.lazytransformablelayout

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LazyTransformableLayoutGestureTest {

    private val layoutBounds = DpRect(0.dp, 0.dp, 10_000.dp, 6_000.dp)

    @Test
    fun `given a drag, should pan the content with the pointer`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val before = state.centerOffset

        onNodeWithTag("layout").performTouchInput {
            swipe(Offset(300f, 300f), Offset(100f, 300f), durationMillis = 200)
        }
        waitForIdle()

        assertTrue(state.centerOffset.x > before.x, "center ${state.centerOffset} did not move right of $before")
    }

    @Test
    fun `given a pinch out, should zoom in`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)

        onNodeWithTag("layout").performTouchInput {
            pinch(
                start0 = Offset(200f, 350f),
                end0 = Offset(100f, 350f),
                start1 = Offset(300f, 350f),
                end1 = Offset(400f, 350f),
                durationMillis = 200
            )
        }
        waitForIdle()

        assertTrue(state.zoomScale > 1.2f, "scale was ${state.zoomScale}")
    }

    @Test
    fun `given a pinch in, should zoom out`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds, initialZoomScale = 2f)
        setMeasuredLayout(state, 500, 700)

        onNodeWithTag("layout").performTouchInput {
            pinch(
                start0 = Offset(100f, 350f),
                end0 = Offset(200f, 350f),
                start1 = Offset(400f, 350f),
                end1 = Offset(300f, 350f),
                durationMillis = 200
            )
        }
        waitForIdle()

        assertTrue(state.zoomScale < 1.8f, "scale was ${state.zoomScale}")
    }

    @Test
    fun `given two fingers twisting, should rotate`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)

        onNodeWithTag("layout").performTouchInput {
            pinch(
                start0 = Offset(150f, 350f),
                end0 = Offset(250f, 150f),
                start1 = Offset(350f, 350f),
                end1 = Offset(250f, 550f),
                durationMillis = 200
            )
        }
        waitForIdle()

        assertNotEquals(0f, state.rotationAngle)
    }

    @Test
    fun `given the plus key, when pressed, should zoom in`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = onNodeWithTag("layout")
        node.performTouchInput { click(Offset(250f, 350f)) }
        waitForIdle()
        mainClock.autoAdvance = false

        tapKey(node, Key.Plus)

        assertEquals(2f, state.zoomScale, 0.05f)
    }

    private fun ComposeUiTest.tapKey(node: SemanticsNodeInteraction, key: Key) {
        node.performKeyInput { keyDown(key) }
        mainClock.advanceTimeByFrame()
        node.performKeyInput { keyUp(key) }
        mainClock.advanceTimeBy(2_000)
    }

    private fun ComposeUiTest.focusLayout(): SemanticsNodeInteraction {
        val node = onNodeWithTag("layout")
        node.performTouchInput { click(Offset(250f, 350f)) }
        waitForIdle()
        return node
    }

    @Test
    fun `given the page up key, when pressed, should zoom in as the plus key does`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        mainClock.autoAdvance = false

        tapKey(node, Key.PageUp)

        assertEquals(2f, state.zoomScale, 0.05f)
    }

    @Test
    fun `given the page down key, when pressed, should zoom out as the minus key does`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds, initialZoomScale = 2f)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        mainClock.autoAdvance = false

        tapKey(node, Key.PageDown)

        assertEquals(1f, state.zoomScale, 0.05f)
    }

    @Test
    fun `given the minus key, when pressed, should zoom out`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds, initialZoomScale = 2f)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        mainClock.autoAdvance = false

        tapKey(node, Key.Minus)

        assertEquals(1f, state.zoomScale, 0.05f)
    }

    @Test
    fun `given the equals and numpad keys, when pressed, should zoom`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        mainClock.autoAdvance = false

        tapKey(node, Key.Equals)
        assertEquals(2f, state.zoomScale, 0.05f)

        tapKey(node, Key.NumPadAdd)
        assertEquals(4f, state.zoomScale, 0.1f)

        tapKey(node, Key.NumPadSubtract)
        assertEquals(2f, state.zoomScale, 0.05f)
    }

    @Test
    fun `given an arrow key, when pressed, should pan by a fixed step of 100dp`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        val before = state.centerOffset
        mainClock.autoAdvance = false

        node.performKeyInput { keyDown(Key.DirectionRight) }
        mainClock.advanceTimeByFrame()
        node.performKeyInput { keyUp(Key.DirectionRight) }
        mainClock.advanceTimeBy(2_000)

        assertEquals(before.x.value + 100f, state.centerOffset.x.value, 0.5f)
        assertEquals(before.y.value, state.centerOffset.y.value, 0.5f)
    }

    @Test
    fun `given a horizontal and a vertical arrow held together, when both are down, should pan on both axes`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        val before = state.centerOffset
        mainClock.autoAdvance = false

        node.performKeyInput {
            keyDown(Key.DirectionRight)
            keyDown(Key.DirectionDown)
        }
        mainClock.advanceTimeByFrame()
        node.performKeyInput {
            keyUp(Key.DirectionRight)
            keyUp(Key.DirectionDown)
        }
        mainClock.advanceTimeBy(2_000)

        assertEquals(before.x.value + 100f, state.centerOffset.x.value, 0.5f)
        assertEquals(before.y.value + 100f, state.centerOffset.y.value, 0.5f)
    }

    @Test
    fun `given an arrow key that was released, when another arrow is pressed, should pan only on the new axis`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        mainClock.autoAdvance = false

        node.performKeyInput { keyDown(Key.DirectionRight) }
        mainClock.advanceTimeByFrame()
        node.performKeyInput { keyUp(Key.DirectionRight) }
        mainClock.advanceTimeBy(2_000)
        val afterRight = state.centerOffset

        node.performKeyInput { keyDown(Key.DirectionDown) }
        mainClock.advanceTimeByFrame()
        node.performKeyInput { keyUp(Key.DirectionDown) }
        mainClock.advanceTimeBy(2_000)

        assertEquals(afterRight.x.value, state.centerOffset.x.value, 0.5f)
        assertEquals(afterRight.y.value + 100f, state.centerOffset.y.value, 0.5f)
    }

    @Test
    fun `given two arrow keys held, when one is released, should keep panning in the direction of the other`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        mainClock.autoAdvance = false

        node.performKeyInput {
            keyDown(Key.DirectionRight)
            keyDown(Key.DirectionDown)
        }
        mainClock.advanceTimeBy(200)
        node.performKeyInput { keyUp(Key.DirectionDown) }
        mainClock.advanceTimeBy(100)
        val afterRelease = state.centerOffset
        mainClock.advanceTimeBy(500)

        assertTrue(state.centerOffset.x.value > afterRelease.x.value + 100f, "center ${state.centerOffset} did not keep moving right of $afterRelease")
        assertEquals(afterRelease.y.value, state.centerOffset.y.value, 0.5f)
        node.performKeyInput { keyUp(Key.DirectionRight) }
    }

    @Test
    fun `given an arrow key held, when time passes without key repeat events, should keep panning`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        val before = state.centerOffset
        mainClock.autoAdvance = false

        node.performKeyInput { keyDown(Key.DirectionRight) }
        mainClock.advanceTimeBy(1_000)

        val whileHeld = state.centerOffset
        assertTrue(whileHeld.x.value > before.x.value + 300f, "center $whileHeld barely moved from $before")

        node.performKeyInput { keyUp(Key.DirectionRight) }
        mainClock.advanceTimeBy(1_000)
        val afterRelease = state.centerOffset
        mainClock.advanceTimeBy(1_000)

        assertEquals(afterRelease.x.value, state.centerOffset.x.value, 0.5f)
    }

    @Test
    fun `given a held arrow key, when a second arrow is pressed, should pan on both axes without waiting for a repeat`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        mainClock.autoAdvance = false

        node.performKeyInput { keyDown(Key.DirectionRight) }
        mainClock.advanceTimeBy(500)
        val beforeSecond = state.centerOffset
        node.performKeyInput { keyDown(Key.DirectionDown) }
        mainClock.advanceTimeBy(200)

        assertTrue(state.centerOffset.x.value > beforeSecond.x.value, "center ${state.centerOffset} did not keep moving right of $beforeSecond")
        assertTrue(state.centerOffset.y.value > beforeSecond.y.value + 20f, "center ${state.centerOffset} did not move down from $beforeSecond")
        node.performKeyInput {
            keyUp(Key.DirectionRight)
            keyUp(Key.DirectionDown)
        }
    }

    @Test
    fun `given a zoom key held, when time passes without key repeat events, should keep zooming and stop after key up`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        mainClock.autoAdvance = false

        node.performKeyInput { keyDown(Key.Plus) }
        mainClock.advanceTimeBy(1_000)

        val whileHeld = state.zoomScale
        assertTrue(whileHeld > 4f, "zoom $whileHeld barely changed while held")

        node.performKeyInput { keyUp(Key.Plus) }
        mainClock.advanceTimeBy(1_000)
        val afterRelease = state.zoomScale
        mainClock.advanceTimeBy(1_000)

        assertEquals(afterRelease, state.zoomScale, 1e-3f)
    }

    @Test
    fun `given a held zoom key, when an arrow is pressed, should zoom and pan together`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        val before = state.centerOffset
        mainClock.autoAdvance = false

        node.performKeyInput { keyDown(Key.Plus) }
        mainClock.advanceTimeBy(200)
        val zoomBeforeArrow = state.zoomScale
        node.performKeyInput { keyDown(Key.DirectionRight) }
        mainClock.advanceTimeBy(500)

        assertTrue(state.zoomScale > zoomBeforeArrow, "zoom ${state.zoomScale} did not keep growing from $zoomBeforeArrow")
        assertTrue(state.centerOffset.x.value != before.x.value, "center ${state.centerOffset} did not pan from $before")
        node.performKeyInput {
            keyUp(Key.Plus)
            keyUp(Key.DirectionRight)
        }
    }

    @Test
    fun `given blocked input, when keys are pressed, should ignore them`()= runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        mainClock.autoAdvance = false

        state.requestAnimateTo(
            zoomScale = 3f,
            animationSpec = tween(300, easing = LinearEasing),
            mutatePriority = MutatePriority.PreventUserInput
        )
        node.performKeyInput {
            keyDown(Key.Plus)
            keyDown(Key.DirectionRight)
        }
        mainClock.advanceTimeBy(200)
        node.performKeyInput {
            keyUp(Key.Plus)
            keyUp(Key.DirectionRight)
        }
        mainClock.advanceTimeBy(2_000)

        assertEquals(3f, state.zoomScale, 1e-3f)
        assertEquals(5_000f, state.centerOffset.x.value, 0.5f)
    }

    @Test
    fun `given a blocking animation that ended, when keys are pressed, should work`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()

        state.requestAnimateTo(
            zoomScale = 2f,
            animationSpec = tween(300, easing = LinearEasing),
            mutatePriority = MutatePriority.PreventUserInput
        )
        waitForIdle()
        mainClock.autoAdvance = false
        tapKey(node, Key.Plus)

        assertEquals(4f, state.zoomScale, 0.1f)
    }

    @Test
    fun `given a double tap, should zoom in`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)

        onNodeWithTag("layout").performTouchInput { doubleClick(Offset(250f, 350f)) }
        waitForIdle()

        assertEquals(2f, state.zoomScale, 0.05f)
    }

    @Test
    fun `given a hovering mouse, when not pressing, should not start a gesture`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val before = Triple(state.rotationAngle, state.zoomScale, state.centerOffset)

        onNodeWithTag("layout").performMouseInput {
            moveTo(Offset(100f, 100f))
            moveTo(Offset(300f, 200f))
            moveTo(Offset(400f, 500f))
        }
        waitForIdle()

        assertEquals(before, Triple(state.rotationAngle, state.zoomScale, state.centerOffset))
    }

    @Test
    fun `given shift held, when hovering, should not rotate`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val before = Triple(state.rotationAngle, state.zoomScale, state.centerOffset)

        onNodeWithTag("layout").performKeyInput { keyDown(Key.ShiftLeft) }
        onNodeWithTag("layout").performMouseInput {
            moveTo(Offset(100f, 100f))
            moveTo(Offset(300f, 200f))
        }
        waitForIdle()

        assertEquals(before, Triple(state.rotationAngle, state.zoomScale, state.centerOffset))
    }

    @Test
    fun `given the primary mouse button, when dragging, should pan`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val before = state.centerOffset

        onNodeWithTag("layout").performMouseInput {
            moveTo(Offset(300f, 300f))
            press(MouseButton.Primary)
            moveTo(Offset(250f, 300f))
            moveTo(Offset(200f, 300f))
            moveTo(Offset(100f, 300f))
            release(MouseButton.Primary)
        }
        waitForIdle()

        assertTrue(state.centerOffset.x > before.x, "center ${state.centerOffset} did not move right of $before")
        assertEquals(0f, state.rotationAngle)
    }

    @Test
    fun `given the secondary mouse button, when dragging, should not pan`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val before = state.centerOffset

        onNodeWithTag("layout").performMouseInput {
            moveTo(Offset(300f, 300f))
            press(MouseButton.Secondary)
            moveTo(Offset(200f, 300f))
            moveTo(Offset(100f, 300f))
            release(MouseButton.Secondary)
        }
        waitForIdle()

        assertEquals(before, state.centerOffset)
    }

    @Test
    fun `given shift held at press time, when dragging, should rotate`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = onNodeWithTag("layout")

        node.performKeyInput { keyDown(Key.ShiftLeft) }
        node.performMouseInput {
            moveTo(Offset(300f, 300f))
            press(MouseButton.Primary)
            moveTo(Offset(250f, 300f))
            moveTo(Offset(200f, 300f))
            moveTo(Offset(100f, 300f))
            release(MouseButton.Primary)
        }
        node.performKeyInput { keyUp(Key.ShiftLeft) }
        waitForIdle()

        assertNotEquals(0f, state.rotationAngle)
        assertEquals(5_000f, state.centerOffset.x.value, 0.5f)
    }

    @Test
    fun `given shift held at press time, when dragging right, should rotate to a negative angle`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = onNodeWithTag("layout")

        node.performKeyInput { keyDown(Key.ShiftLeft) }
        node.performMouseInput {
            moveTo(Offset(100f, 300f))
            press(MouseButton.Primary)
            moveTo(Offset(150f, 300f))
            moveTo(Offset(200f, 300f))
            moveTo(Offset(300f, 300f))
            release(MouseButton.Primary)
        }
        node.performKeyInput { keyUp(Key.ShiftLeft) }
        waitForIdle()

        assertTrue(state.rotationAngle < 0f, "angle was ${state.rotationAngle}")
    }

    @Test
    fun `given shift held at press time, when dragging left, should rotate to a positive angle`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = onNodeWithTag("layout")

        node.performKeyInput { keyDown(Key.ShiftLeft) }
        node.performMouseInput {
            moveTo(Offset(300f, 300f))
            press(MouseButton.Primary)
            moveTo(Offset(250f, 300f))
            moveTo(Offset(200f, 300f))
            moveTo(Offset(100f, 300f))
            release(MouseButton.Primary)
        }
        node.performKeyInput { keyUp(Key.ShiftLeft) }
        waitForIdle()

        assertTrue(state.rotationAngle > 0f, "angle was ${state.rotationAngle}")
    }

    @Test
    fun `given shift released before press, when dragging, should not rotate`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = onNodeWithTag("layout")

        node.performKeyInput { keyDown(Key.ShiftLeft) }
        node.performMouseInput { moveTo(Offset(300f, 300f)) }
        node.performKeyInput { keyUp(Key.ShiftLeft) }
        node.performMouseInput {
            press(MouseButton.Primary)
            moveTo(Offset(200f, 300f))
            moveTo(Offset(100f, 300f))
            release(MouseButton.Primary)
        }
        waitForIdle()

        assertEquals(0f, state.rotationAngle)
        assertTrue(state.centerOffset.x.value > 5_000f)
    }

    @Test
    fun `given asymmetric padding, when dragging with shift, should rotate about the content center`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(
            state,
            500,
            700,
            contentPadding = PaddingValues(start = 200.dp, top = 0.dp, end = 0.dp, bottom = 100.dp)
        )
        val node = onNodeWithTag("layout")
        val center = state.centerOffset
        val before = state.layoutToViewport(center)

        node.performKeyInput { keyDown(Key.ShiftLeft) }
        node.performMouseInput {
            moveTo(Offset(300f, 300f))
            press(MouseButton.Primary)
            moveTo(Offset(250f, 300f))
            moveTo(Offset(200f, 300f))
            moveTo(Offset(100f, 300f))
            release(MouseButton.Primary)
        }
        node.performKeyInput { keyUp(Key.ShiftLeft) }
        waitForIdle()

        assertNotEquals(0f, state.rotationAngle)
        val after = state.layoutToViewport(center)
        assertEquals(before.x, after.x, 0.5f)
        assertEquals(before.y, after.y, 0.5f)
        assertEquals(350f, after.x, 0.5f)
        assertEquals(300f, after.y, 0.5f)
    }

    @Test
    fun `given a focused child that consumes keys, when keys are pressed, should win over the layout`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        val childFocus = FocusRequester()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(500.dp, 700.dp)) {
                    LazyTransformableLayout(
                        state = state,
                        modifier = Modifier.testTag("layout"),
                        overscrollEffect = null
                    ) {
                        item(DpRect(4_900.dp, 2_900.dp, 5_100.dp, 3_100.dp), key = "child") {
                            Box(
                                Modifier.size(200.dp)
                                    .testTag("child")
                                    .focusRequester(childFocus)
                                    .onKeyEvent { true }
                                    .focusable()
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        runOnIdle { childFocus.requestFocus() }
        waitForIdle()
        val before = Triple(state.zoomScale, state.centerOffset, state.rotationAngle)

        onNodeWithTag("child").performKeyInput {
            pressKey(Key.Plus)
            pressKey(Key.DirectionRight)
        }
        waitForIdle()

        assertEquals(before, Triple(state.zoomScale, state.centerOffset, state.rotationAngle))
    }

    private fun LazyTransformableLayoutState.layoutAt(position: Offset) =
        viewportToLayout(position).let { Offset(it.x.value, it.y.value) }

    @Test
    fun `given wheel scrolling up, should zoom in about the cursor`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val cursor = Offset(100f, 200f)
        val before = state.layoutAt(cursor)

        onNodeWithTag("layout").performMouseInput {
            moveTo(cursor)
            scroll(-1f)
        }
        waitForIdle()

        assertTrue(state.zoomScale > 1f, "scale was ${state.zoomScale}")
        val after = state.layoutAt(cursor)
        assertEquals(before.x, after.x, 0.1f)
        assertEquals(before.y, after.y, 0.1f)
    }

    @Test
    fun `given wheel scrolling down, should zoom out about the cursor`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds, initialZoomScale = 2f)
        setMeasuredLayout(state, 500, 700)
        val cursor = Offset(400f, 500f)
        val before = state.layoutAt(cursor)

        onNodeWithTag("layout").performMouseInput {
            moveTo(cursor)
            scroll(1f)
        }
        waitForIdle()

        assertTrue(state.zoomScale < 2f, "scale was ${state.zoomScale}")
        val after = state.layoutAt(cursor)
        assertEquals(before.x, after.x, 0.1f)
        assertEquals(before.y, after.y, 0.1f)
    }

    @Test
    fun `given a wheel notch, when scrolling, should zoom by a fixed factor`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)

        onNodeWithTag("layout").performMouseInput {
            moveTo(Offset(250f, 350f))
            scroll(-1f)
        }
        waitForIdle()

        assertEquals(1.1f, state.zoomScale, 1e-3f)
    }

    @Test
    fun `given a wheel zoom, should animate about the cursor and end at the target scale`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val cursor = Offset(100f, 200f)
        val before = state.layoutAt(cursor)
        mainClock.autoAdvance = false

        onNodeWithTag("layout").performMouseInput {
            moveTo(cursor)
            scroll(-2f)
        }
        var partway = false
        repeat(300) {
            mainClock.advanceTimeByFrame()
            val after = state.layoutAt(cursor)
            assertEquals(before.x, after.x, 0.1f)
            assertEquals(before.y, after.y, 0.1f)
            if (state.zoomScale > 1.001f && state.zoomScale < 1.1f.pow(2) - 0.001f) partway = true
        }

        assertTrue(partway, "zoom jumped straight to ${state.zoomScale}")
        assertEquals(1.1f.pow(2), state.zoomScale, 1e-3f)
    }

    @Test
    fun `given a wheel zoom, before the first frame, should not have changed the scale`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        mainClock.autoAdvance = false

        onNodeWithTag("layout").performMouseInput {
            moveTo(Offset(100f, 200f))
            scroll(-1f)
        }

        assertEquals(1f, state.zoomScale)
    }

    @Test
    fun `given several wheel notches, when scrolling, should zoom by the factor to the power of the notches`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)

        onNodeWithTag("layout").performMouseInput {
            moveTo(Offset(250f, 350f))
            scroll(-3f)
        }
        waitForIdle()

        assertEquals(1.1f.pow(3), state.zoomScale, 1e-3f)
    }

    @Test
    fun `given fractional wheel deltas from a trackpad, should zoom proportionally`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)

        onNodeWithTag("layout").performMouseInput {
            moveTo(Offset(250f, 350f))
            scroll(-0.25f)
        }
        waitForIdle()

        assertEquals(1.1f.pow(0.25f), state.zoomScale, 1e-3f)
    }

    @Test
    fun `given a wheel event consumed by a child, should not zoom`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(500.dp, 700.dp)) {
                    LazyTransformableLayout(state, modifier = Modifier.testTag("layout"), overscrollEffect = null) {
                        item(DpRect(4_000.dp, 2_000.dp, 6_000.dp, 4_000.dp)) {
                            Box(
                                Modifier.fillMaxSize().pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithTag("layout").performMouseInput {
            moveTo(Offset(250f, 350f))
            scroll(-1f)
        }
        waitForIdle()

        assertEquals(1f, state.zoomScale)
    }

    @Test
    fun `given a shift drag rotation that flings, should stay finite and keep the content center fixed`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(
            state,
            500,
            700,
            contentPadding = PaddingValues(start = 200.dp, top = 0.dp, end = 0.dp, bottom = 100.dp)
        )
        val node = onNodeWithTag("layout")
        val center = state.centerOffset

        node.performKeyInput { keyDown(Key.ShiftLeft) }
        node.performMouseInput {
            moveTo(Offset(450f, 300f))
            press(MouseButton.Primary)
            moveTo(Offset(400f, 300f))
            moveTo(Offset(300f, 300f))
            moveTo(Offset(100f, 300f))
            release(MouseButton.Primary)
        }
        node.performKeyInput { keyUp(Key.ShiftLeft) }
        waitForIdle()
        mainClock.advanceTimeBy(5_000)

        assertTrue(state.rotationAngle.isFinite() && state.rotationAngle != 0f, "angle was ${state.rotationAngle}")
        assertTrue(state.zoomScale.isFinite())
        val after = state.layoutToViewport(center)
        assertEquals(350f, after.x, 0.5f)
        assertEquals(300f, after.y, 0.5f)
    }

    @Test
    fun `given blocked input, when wheel scrolling, should ignore it`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        mainClock.autoAdvance = false

        state.requestAnimateTo(
            zoomScale = 3f,
            animationSpec = tween(300, easing = LinearEasing),
            mutatePriority = MutatePriority.PreventUserInput
        )
        onNodeWithTag("layout").performMouseInput {
            moveTo(Offset(100f, 200f))
            scroll(1f)
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(2_000)

        assertEquals(3f, state.zoomScale, 1e-3f)
    }

    @Test
    fun `given a running animation, when scrolling the wheel horizontally, should not cancel it`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        mainClock.autoAdvance = false

        state.requestAnimateTo(zoomScale = 3f, animationSpec = tween(300, easing = LinearEasing))
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(100)
        onNodeWithTag("layout").performMouseInput {
            moveTo(Offset(100f, 200f))
            scroll(1f, ScrollWheel.Horizontal)
        }
        mainClock.advanceTimeBy(2_000)

        assertEquals(3f, state.zoomScale, 1e-3f)
    }

    @Test
    fun `given a running animation, when scrolling the wheel vertically, should zoom`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        mainClock.autoAdvance = false

        state.requestAnimateTo(zoomScale = 3f, animationSpec = tween(300, easing = LinearEasing))
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(100)
        onNodeWithTag("layout").performMouseInput {
            moveTo(Offset(100f, 200f))
            scroll(1f)
        }
        mainClock.advanceTimeBy(2_000)

        assertTrue(state.zoomScale < 3f, "scale was ${state.zoomScale}")
    }

    private fun TouchInjectionScope.tap(position: Offset = Offset(250f, 350f)) {
        down(position)
        advanceEventTime(50)
        up()
        advanceEventTime(100)
    }

    private fun TouchInjectionScope.twoFingerTap() {
        down(0, Offset(200f, 350f))
        advanceEventTime(10)
        down(1, Offset(300f, 350f))
        advanceEventTime(50)
        up(0)
        advanceEventTime(10)
        up(1)
        advanceEventTime(100)
    }

    @Test
    fun `given three quick taps, should zoom in no more than once`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)

        onNodeWithTag("layout").performTouchInput { repeat(3) { tap() } }
        waitForIdle()

        assertTrue(state.zoomScale > 1.1f, "scale was ${state.zoomScale}")
        assertTrue(state.zoomScale <= 2f + 0.05f, "scale was ${state.zoomScale}")
    }

    @Test
    fun `given two quick two-finger taps, should zoom out twice`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds, initialZoomScale = 4f)
        setMeasuredLayout(state, 500, 700)

        onNodeWithTag("layout").performTouchInput { repeat(2) { twoFingerTap() } }
        waitForIdle()

        assertEquals(1f, state.zoomScale, 0.02f)
    }

    @Test
    fun `given three quick two-finger taps, should zoom out three times`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds, initialZoomScale = 4f)
        setMeasuredLayout(state, 500, 700)

        onNodeWithTag("layout").performTouchInput { repeat(3) { twoFingerTap() } }
        waitForIdle()

        assertEquals(0.5f, state.zoomScale, 0.01f)
    }

    @Test
    fun `given a tap followed by a two-finger tap, should zoom out once without zooming in`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds, initialZoomScale = 2f)
        setMeasuredLayout(state, 500, 700)
        mainClock.autoAdvance = false

        onNodeWithTag("layout").performTouchInput {
            tap()
            twoFingerTap()
        }
        var maxZoom = state.zoomScale
        repeat(200) {
            mainClock.advanceTimeByFrame()
            maxZoom = maxOf(maxZoom, state.zoomScale)
        }

        assertTrue(maxZoom <= 2f + 1e-3f, "zoomed in to $maxZoom")
        assertEquals(1f, state.zoomScale, 0.02f)
    }

    private fun ComposeUiTest.scrollEveryFrame(events: Int, scrollY: Float) {
        val node = onNodeWithTag("layout")
        node.performMouseInput { moveTo(Offset(250f, 350f)) }
        repeat(events) {
            node.performMouseInput { scroll(scrollY) }
            mainClock.advanceTimeByFrame()
        }
    }

    @Test
    fun `given wheel events every frame, should zoom by the factor to the power of the total scroll`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        mainClock.autoAdvance = false

        scrollEveryFrame(20, -0.5f)
        mainClock.advanceTimeBy(2_000)

        assertEquals(1.1f.pow(10), state.zoomScale, 0.01f)
    }

    @Test
    fun `given wheel events every frame, should zoom partway before the events stop`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        mainClock.autoAdvance = false

        scrollEveryFrame(20, -0.5f)

        assertTrue(state.zoomScale > 1.1f.pow(5), "scale was only ${state.zoomScale} after the last event")
    }

    @Test
    fun `given a vertical swipe, should pan without zooming`() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val before = state.centerOffset

        onNodeWithTag("layout").performTouchInput {
            swipe(Offset(250f, 500f), Offset(250f, 300f), durationMillis = 200)
        }
        waitForIdle()

        assertEquals(1f, state.zoomScale)
        assertEquals(before.x, state.centerOffset.x)
        assertTrue(state.centerOffset.y > before.y, "center ${state.centerOffset} did not move below $before")
    }
}
