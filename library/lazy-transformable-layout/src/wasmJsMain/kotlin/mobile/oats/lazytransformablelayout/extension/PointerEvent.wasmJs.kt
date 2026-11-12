package mobile.oats.lazytransformablelayout.extension

import androidx.compose.ui.dom.domEventOrNull
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import org.w3c.dom.events.WheelEvent
import kotlin.math.pow

internal actual fun PointerEvent.wheelZoomFactor(scrollY: Float) = 2f.pow(
    -scrollY * when ((domEventOrNull as? WheelEvent)?.deltaMode) {
        WheelEvent.DOM_DELTA_LINE -> 0.05f
        WheelEvent.DOM_DELTA_PAGE -> 1f
        else -> if (keyboardModifiers.isCtrlPressed) {
            0.02f
        } else 0.006f
    }
)
