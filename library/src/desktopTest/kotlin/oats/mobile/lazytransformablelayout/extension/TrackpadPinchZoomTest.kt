package oats.mobile.lazytransformablelayout.extension

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.awt.Component
import javax.swing.JFrame
import javax.swing.JRootPane
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class TrackpadPinchZoomTest {

    private val isMac = System.getProperty("os.name").contains("mac", ignoreCase = true)

    @Test
    fun `given a hovered node, when a magnify is dispatched, should report the hover position and zoom factor`() {
        if (!isMac) return
        runComposeUiTest {
            val zooms = mutableListOf<Pair<Offset, Float>>()
            val window = headlessFrame()
            setContent {
                CompositionLocalProvider(LocalAwtWindow provides window, LocalDensity provides Density(1f)) {
                    Box(
                        Modifier
                            .size(500.dp, 700.dp)
                            .testTag("node")
                            .trackpadPinchZoom({ _, _ -> }) { position, factor -> zooms += position to factor }
                    )
                }
            }
            waitForIdle()
            onNodeWithTag("node").performMouseInput { moveTo(Offset(250f, 350f)) }
            waitForIdle()

            runOnUiThread { dispatchMagnify(window.rootPane, 0.25) }
            waitForIdle()

            assertEquals(listOf(Offset(250f, 350f) to 1.25f.pow(1.5f)), zooms)
        }
    }

    @Test
    fun `given a hovered node that left the composition, when pinch events are dispatched, should not invoke any callback`() {
        if (!isMac) return
        runComposeUiTest {
            var zooms = 0
            var stops = 0
            var shown by mutableStateOf(true)
            val window = headlessFrame()
            setContent {
                CompositionLocalProvider(LocalAwtWindow provides window, LocalDensity provides Density(1f)) {
                    if (shown) {
                        Box(
                            Modifier
                                .size(500.dp, 700.dp)
                                .testTag("node")
                                .trackpadPinchZoom({ _, _ -> stops++ }) { _, _ -> zooms++ }
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("node").performMouseInput { moveTo(Offset(250f, 350f)) }
            waitForIdle()
            runOnUiThread { dispatchMagnify(window.rootPane, 0.25) }
            waitForIdle()
            assertEquals(1, zooms)

            shown = false
            waitForIdle()
            runOnUiThread {
                dispatchMagnify(window.rootPane, 0.25)
                dispatchPhase(window.rootPane, GESTURE_ENDED)
            }
            waitForIdle()

            assertEquals(1, zooms)
            assertEquals(0, stops)
        }
    }

    @Test
    fun `given a hovered node, when the pointer exits and a magnify is dispatched, should not zoom`() {
        if (!isMac) return
        runComposeUiTest {
            var zooms = 0
            val window = headlessFrame()
            setContent {
                CompositionLocalProvider(LocalAwtWindow provides window, LocalDensity provides Density(1f)) {
                    Box(
                        Modifier
                            .size(500.dp, 700.dp)
                            .testTag("node")
                            .trackpadPinchZoom({ _, _ -> }) { _, _ -> zooms++ }
                    )
                }
            }
            waitForIdle()
            onNodeWithTag("node").performMouseInput { moveTo(Offset(250f, 350f)) }
            waitForIdle()
            runOnUiThread { dispatchMagnify(window.rootPane, 0.25) }
            waitForIdle()
            assertEquals(1, zooms)

            onNodeWithTag("node").performMouseInput { exit(Offset(-10f, -10f)) }
            waitForIdle()
            runOnUiThread { dispatchMagnify(window.rootPane, 0.25) }
            waitForIdle()

            assertEquals(1, zooms)
        }
    }

    @Test
    fun `given a hovered node, when the gesture ends without a magnify, should not report a stop`() {
        if (!isMac) return
        runComposeUiTest {
            var stops = 0
            val window = headlessFrame()
            setContent {
                CompositionLocalProvider(LocalAwtWindow provides window, LocalDensity provides Density(1f)) {
                    Box(
                        Modifier
                            .size(500.dp, 700.dp)
                            .testTag("node")
                            .trackpadPinchZoom({ _, _ -> stops++ }) { _, _ -> }
                    )
                }
            }
            waitForIdle()
            onNodeWithTag("node").performMouseInput { moveTo(Offset(250f, 350f)) }
            waitForIdle()

            runOnUiThread { dispatchPhase(window.rootPane, GESTURE_BEGAN) }
            repeat(10) {
                runOnUiThread { dispatchMagnify(window.rootPane, 0.05) }
                Thread.sleep(10)
            }
            runOnUiThread { dispatchPhase(window.rootPane, GESTURE_ENDED) }
            waitForIdle()
            assertEquals(1, stops)

            runOnUiThread { dispatchPhase(window.rootPane, GESTURE_ENDED) }
            waitForIdle()

            assertEquals(1, stops)
        }
    }

    @Test
    fun `given a hovered node, when the zoom lambda is swapped during recomposition, should call only the new lambda`() {
        if (!isMac) return
        runComposeUiTest {
            var oldZooms = 0
            var newZooms = 0
            val oldLambda: (Offset, Float) -> Unit = { _, _ -> oldZooms++ }
            val newLambda: (Offset, Float) -> Unit = { _, _ -> newZooms++ }
            var onZoom by mutableStateOf(oldLambda)
            val window = headlessFrame()
            setContent {
                CompositionLocalProvider(LocalAwtWindow provides window, LocalDensity provides Density(1f)) {
                    Box(
                        Modifier
                            .size(500.dp, 700.dp)
                            .testTag("node")
                            .trackpadPinchZoom({ _, _ -> }, onZoom)
                    )
                }
            }
            waitForIdle()
            onNodeWithTag("node").performMouseInput { moveTo(Offset(250f, 350f)) }
            waitForIdle()
            runOnUiThread { dispatchMagnify(window.rootPane, 0.25) }
            waitForIdle()
            assertEquals(1, oldZooms)

            onZoom = newLambda
            waitForIdle()
            runOnUiThread { dispatchMagnify(window.rootPane, 0.25) }
            waitForIdle()

            assertEquals(1, oldZooms)
            assertEquals(1, newZooms)
        }
    }
}

private const val GESTURE_PACKAGE = "com.apple.eawt.event"
internal const val GESTURE_BEGAN = -1.0
internal const val GESTURE_ENDED = 1.0

internal fun dispatchMagnify(component: Component, magnification: Double) {
    val event = Class.forName("$GESTURE_PACKAGE.MagnificationEvent")
        .getDeclaredConstructor(Double::class.javaPrimitiveType)
        .apply { isAccessible = true }
        .newInstance(magnification)
    val notifier = nextNotifier(component) ?: return
    notifier.javaClass.getDeclaredMethod("recursivelyHandleMagnify", event.javaClass)
        .apply { isAccessible = true }
        .invoke(notifier, event)
}

internal fun dispatchPhase(component: Component, phase: Double) {
    val event = Class.forName("$GESTURE_PACKAGE.GesturePhaseEvent")
        .getDeclaredConstructor()
        .apply { isAccessible = true }
        .newInstance()
    val notifier = nextNotifier(component) ?: return
    notifier.javaClass.getDeclaredMethod(
        "recursivelyHandlePhaseChange",
        Double::class.javaPrimitiveType,
        event.javaClass
    ).apply { isAccessible = true }
        .invoke(notifier, phase, event)
}

private fun nextNotifier(component: Component): Any? =
    Class.forName("$GESTURE_PACKAGE.GestureHandler")
        .getDeclaredMethod("getNextNotifierForComponent", Component::class.java)
        .apply { isAccessible = true }
        .invoke(null, component)

// Tests run headless, where JFrame's constructor throws, so the frame is allocated without running it.
internal fun headlessFrame(): RootPaneFrame {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    return unsafeClass.getMethod("allocateInstance", Class::class.java)
        .invoke(unsafe, RootPaneFrame::class.java) as RootPaneFrame
}

internal class RootPaneFrame : JFrame() {
    private var pane: JRootPane? = null

    override fun getRootPane(): JRootPane = pane ?: JRootPane().also { pane = it }

    override fun toString() = "RootPaneFrame"
}
