package mobile.oats.lazytransformablelayout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import mobile.oats.lazytransformablelayout.extension.GESTURE_BEGAN
import mobile.oats.lazytransformablelayout.extension.GESTURE_ENDED
import mobile.oats.lazytransformablelayout.extension.dispatchMagnify
import mobile.oats.lazytransformablelayout.extension.dispatchPhase
import mobile.oats.lazytransformablelayout.extension.headlessFrame
import javax.swing.JRootPane
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class LazyTransformableLayoutTrackpadPinchTest {

    private val isMac = System.getProperty("os.name").contains("mac", ignoreCase = true)
    private val layoutBounds = DpRect(0.dp, 0.dp, 10_000.dp, 6_000.dp)

    @Test
    fun `given a hovered layout, when a trackpad pinch magnifies, should zoom in by the sped up factor`() {
        if (!isMac) return
        runComposeUiTest {
            val state = LazyTransformableLayoutState(layoutBounds)
            val rootPane = setLayoutInWindow(state)
            hover()

            runOnUiThread { dispatchMagnify(rootPane, 0.25) }
            waitForIdle()

            assertEquals(1.25f.pow(1.5f), state.zoomScale, 0.001f)
        }
    }

    @Test
    fun `given a layout that is not hovered, when a trackpad pinch magnifies, should not zoom`() {
        if (!isMac) return
        runComposeUiTest {
            val state = LazyTransformableLayoutState(layoutBounds)
            val rootPane = setLayoutInWindow(state)

            runOnUiThread { dispatchMagnify(rootPane, 0.25) }
            waitForIdle()

            assertEquals(1f, state.zoomScale)
        }
    }

    @Test
    fun `given a quick trackpad pinch, when the gesture ends, should keep zooming in`() {
        if (!isMac) return
        runComposeUiTest {
            val state = LazyTransformableLayoutState(layoutBounds)
            val rootPane = setLayoutInWindow(state)
            hover()

            pinch(rootPane)
            val endScale = runOnUiThread {
                dispatchPhase(rootPane, GESTURE_ENDED)
                state.zoomScale
            }
            waitForIdle()

            assertTrue(state.zoomScale > endScale * 1.05f, "scale ${state.zoomScale} did not grow past $endScale")
        }
    }

    @Test
    fun `given a trackpad pinch held still, when the gesture ends, should not keep zooming`() {
        if (!isMac) return
        runComposeUiTest {
            val state = LazyTransformableLayoutState(layoutBounds)
            val rootPane = setLayoutInWindow(state)
            hover()

            pinch(rootPane)
            Thread.sleep(200)
            val endScale = runOnUiThread {
                dispatchPhase(rootPane, GESTURE_ENDED)
                state.zoomScale
            }
            waitForIdle()

            assertTrue(endScale > 1f, "pinch did not zoom")
            assertEquals(endScale, state.zoomScale)
        }
    }

    private fun ComposeUiTest.pinch(rootPane: JRootPane) {
        runOnUiThread { dispatchPhase(rootPane, GESTURE_BEGAN) }
        repeat(10) {
            runOnUiThread { dispatchMagnify(rootPane, 0.05) }
            Thread.sleep(10)
        }
    }

    private fun ComposeUiTest.hover() {
        onNodeWithTag("layout").performMouseInput { moveTo(Offset(250f, 350f)) }
        waitForIdle()
    }

    private fun ComposeUiTest.setLayoutInWindow(state: LazyTransformableLayoutState): JRootPane {
        val window = headlessFrame()
        setContent {
            CompositionLocalProvider(LocalAwtWindow provides window, LocalDensity provides Density(1f)) {
                Box(Modifier.size(500.dp, 700.dp)) {
                    LazyTransformableLayout(
                        state = state,
                        modifier = Modifier.testTag("layout"),
                        overscrollEffect = null
                    ) {}
                }
            }
        }
        waitForIdle()
        return window.rootPane
    }
}
