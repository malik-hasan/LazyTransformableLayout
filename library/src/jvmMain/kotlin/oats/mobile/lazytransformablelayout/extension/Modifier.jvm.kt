package oats.mobile.lazytransformablelayout.extension

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.unit.Velocity
import java.lang.reflect.Proxy
import javax.swing.JComponent
import javax.swing.RootPaneContainer
import kotlin.math.ln
import kotlin.math.pow

private const val GESTURE_PACKAGE = "com.apple.eawt.event"

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.trackpadPinchZoom(
    onZoomStopped: (position: Offset, logZoomVelocity: Float) -> Unit,
    onZoom: (position: Offset, zoomFactor: Float) -> Unit
): Modifier = composed {
    val latestOnZoomStopped by rememberUpdatedState(onZoomStopped)
    val latestOnZoom by rememberUpdatedState(onZoom)
    val rootPane = (LocalAwtWindow.current as? RootPaneContainer)?.rootPane

    pointerInput(rootPane) {
        var hover: Offset? = null
        var zoomed = false
        val logZoomVelocityTracker = VelocityTracker1D(true)
        fun uptimeMillis() = System.nanoTime() / 1_000_000

        val removeListener = rootPane?.let { component ->
            runCatching {
                val magnificationListener = Class.forName("$GESTURE_PACKAGE.MagnificationListener")
                val phaseListener = Class.forName("$GESTURE_PACKAGE.GesturePhaseListener")
                val gestureListener = Class.forName("$GESTURE_PACKAGE.GestureListener")
                val getMagnification = Class.forName("$GESTURE_PACKAGE.MagnificationEvent").getMethod("getMagnification")
                val utilities = Class.forName("$GESTURE_PACKAGE.GestureUtilities")

                val listener = Proxy.newProxyInstance(
                    magnificationListener.classLoader,
                    arrayOf(magnificationListener, phaseListener)
                ) { proxy, method, args ->
                    when (method.name) {
                        "magnify" -> hover?.let { position ->
                            val magnification = getMagnification.invoke(args[0]) as Double
                            val zoomFactor = (1 + magnification).toFloat().coerceAtLeast(0.01f).pow(1.5f)
                            zoomed = true
                            logZoomVelocityTracker.addDataPoint(uptimeMillis(), ln(zoomFactor))
                            latestOnZoom(position, zoomFactor)
                        }

                        "gestureEnded" -> {
                            hover?.takeIf { zoomed }?.let { position ->
                                logZoomVelocityTracker.addDataPoint(uptimeMillis(), 0f)
                                stopWithFlingThresholds(
                                    onTransformStopped = { centroid, _, logZoomVelocity, _ ->
                                        latestOnZoomStopped(centroid, logZoomVelocity)
                                    },
                                    centroid = position,
                                    rotationVelocity = 0f,
                                    logZoomVelocity = logZoomVelocityTracker.calculateVelocity(),
                                    panVelocity = Velocity.Zero,
                                    maxCentroidSize = size.width.coerceAtMost(size.height) / 2f
                                )
                            }
                            zoomed = false
                            logZoomVelocityTracker.resetTracking()
                        }

                        "equals" -> return@newProxyInstance proxy === args?.firstOrNull()
                        "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                        "toString" -> return@newProxyInstance "MagnificationListener"
                    }
                    null
                }

                utilities.getMethod("addGestureListenerTo", JComponent::class.java, gestureListener)
                    .invoke(null, component, listener)

                val remove = {
                    utilities.getMethod("removeGestureListenerFrom", JComponent::class.java, gestureListener)
                        .invoke(null, component, listener)
                }
                remove
            }.getOrNull()
        }

        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    hover = if (event.type == PointerEventType.Exit) null else event.changes.first().position
                }
            }
        } finally {
            removeListener?.invoke()
        }
    }
}
