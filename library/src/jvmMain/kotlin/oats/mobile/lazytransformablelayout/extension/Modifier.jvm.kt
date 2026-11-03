package oats.mobile.lazytransformablelayout.extension

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import java.lang.reflect.Proxy
import javax.swing.JComponent
import javax.swing.RootPaneContainer

private const val GESTURE_PACKAGE = "com.apple.eawt.event"

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.trackpadPinchZoom(
    onZoom: (position: Offset, zoomFactor: Float) -> Unit
): Modifier = composed {
    val latestOnZoom by rememberUpdatedState(onZoom)
    val hover = remember { arrayOfNulls<Offset>(1) }
    val rootPane = (LocalAwtWindow.current as? RootPaneContainer)?.rootPane

    DisposableEffect(rootPane) {
        val removeListener = rootPane?.let {
            addMagnificationListener(it) { magnification ->
                hover[0]?.let { position ->
                    latestOnZoom(position, (1 + magnification).toFloat().coerceAtLeast(0.01f))
                }
            }
        }
        onDispose { removeListener?.invoke() }
    }

    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                hover[0] = if (event.type == PointerEventType.Exit) null else event.changes.first().position
            }
        }
    }
}

internal fun addMagnificationListener(component: JComponent, onMagnify: (Double) -> Unit) = runCatching {
    val listenerInterface = Class.forName("$GESTURE_PACKAGE.MagnificationListener")
    val gestureListenerInterface = Class.forName("$GESTURE_PACKAGE.GestureListener")
    val getMagnification = Class.forName("$GESTURE_PACKAGE.MagnificationEvent").getMethod("getMagnification")
    val utilities = Class.forName("$GESTURE_PACKAGE.GestureUtilities")

    val listener = Proxy.newProxyInstance(listenerInterface.classLoader, arrayOf(listenerInterface)) { proxy, method, args ->
        when (method.name) {
            "magnify" -> onMagnify(getMagnification.invoke(args[0]) as Double)
            "equals" -> return@newProxyInstance proxy === args?.firstOrNull()
            "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
            "toString" -> return@newProxyInstance "MagnificationListener"
        }
        null
    }

    utilities.getMethod("addGestureListenerTo", JComponent::class.java, gestureListenerInterface)
        .invoke(null, component, listener)

    val remove: () -> Unit = {
        utilities.getMethod("removeGestureListenerFrom", JComponent::class.java, gestureListenerInterface)
            .invoke(null, component, listener)
    }
    remove
}.getOrNull()
