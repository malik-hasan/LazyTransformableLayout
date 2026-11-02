package oats.mobile.lazytransformablelayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LazyTransformableLayoutGestureTest {

    private val layoutBounds = DpRect(0.dp, 0.dp, 10_000.dp, 6_000.dp)

    @Test
    fun draggingPansTheContentWithThePointer() = runComposeUiTest {
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
    fun pinchingOutZoomsIn() = runComposeUiTest {
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
    fun pinchingInZoomsOut() = runComposeUiTest {
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
    fun twistingTwoFingersRotates() = runComposeUiTest {
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
    fun plusKeyZoomsIn() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = onNodeWithTag("layout")
        node.performTouchInput { click(Offset(250f, 350f)) }
        waitForIdle()

        node.performKeyInput { pressKey(Key.Plus) }
        waitForIdle()

        assertEquals(2f, state.zoomScale, 0.05f)
    }

    private fun ComposeUiTest.focusLayout(): SemanticsNodeInteraction {
        val node = onNodeWithTag("layout")
        node.performTouchInput { click(Offset(250f, 350f)) }
        waitForIdle()
        return node
    }

    @Test
    fun pageUpZoomsInLikePlus() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()

        node.performKeyInput { pressKey(Key.PageUp) }
        waitForIdle()

        assertEquals(2f, state.zoomScale, 0.05f)
    }

    @Test
    fun pageDownZoomsOutLikeMinus() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds, initialZoomScale = 2f)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()

        node.performKeyInput { pressKey(Key.PageDown) }
        waitForIdle()

        assertEquals(1f, state.zoomScale, 0.05f)
    }

    @Test
    fun minusKeyZoomsOut() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds, initialZoomScale = 2f)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()

        node.performKeyInput { pressKey(Key.Minus) }
        waitForIdle()

        assertEquals(1f, state.zoomScale, 0.05f)
    }

    @Test
    fun equalsAndNumPadKeysZoom() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()

        node.performKeyInput { pressKey(Key.Equals) }
        waitForIdle()
        assertEquals(2f, state.zoomScale, 0.05f)

        node.performKeyInput { pressKey(Key.NumPadAdd) }
        waitForIdle()
        assertEquals(4f, state.zoomScale, 0.1f)

        node.performKeyInput { pressKey(Key.NumPadSubtract) }
        waitForIdle()
        assertEquals(2f, state.zoomScale, 0.05f)
    }

    @Test
    fun arrowKeyPansByAFixedStepOf100Dp() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        val before = state.centerOffset

        node.performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()

        assertEquals(before.x.value + 100f, state.centerOffset.x.value, 0.5f)
        assertEquals(before.y.value, state.centerOffset.y.value, 0.5f)
    }

    @Test
    fun keysAreIgnoredWhileInputIsBlocked() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()
        mainClock.autoAdvance = false

        state.requestAnimateTo(
            zoomScale = 3f,
            animationSpec = tween(300, easing = LinearEasing),
            mutatePriority = MutatePriority.PreventUserInput
        )
        node.performKeyInput { pressKey(Key.Plus) }
        node.performKeyInput { pressKey(Key.DirectionRight) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(2_000)

        assertEquals(3f, state.zoomScale, 1e-3f)
        assertEquals(5_000f, state.centerOffset.x.value, 0.5f)
    }

    @Test
    fun keysWorkAfterABlockingAnimationEnds() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)
        val node = focusLayout()

        state.requestAnimateTo(
            zoomScale = 2f,
            animationSpec = tween(300, easing = LinearEasing),
            mutatePriority = MutatePriority.PreventUserInput
        )
        waitForIdle()
        node.performKeyInput { pressKey(Key.Plus) }
        waitForIdle()

        assertEquals(4f, state.zoomScale, 0.1f)
    }

    @Test
    fun doubleTapZoomsIn() = runComposeUiTest {
        val state = LazyTransformableLayoutState(layoutBounds)
        setMeasuredLayout(state, 500, 700)

        onNodeWithTag("layout").performTouchInput { doubleClick(Offset(250f, 350f)) }
        waitForIdle()

        assertEquals(2f, state.zoomScale, 0.05f)
    }

    @Test
    fun hoveringTheMouseWithoutPressingDoesNotStartAGesture() = runComposeUiTest {
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
    fun hoveringWithShiftHeldDoesNotRotate() = runComposeUiTest {
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
    fun draggingTheMouseWithThePrimaryButtonPans() = runComposeUiTest {
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
    fun draggingTheMouseWithTheSecondaryButtonDoesNotPan() = runComposeUiTest {
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
    fun shiftHeldAtPressTimeRotates() = runComposeUiTest {
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
    fun shiftReleasedBeforePressDoesNotRotate() = runComposeUiTest {
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
    fun shiftDragRotatesAboutTheContentCenterWithAsymmetricPadding() = runComposeUiTest {
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
}
