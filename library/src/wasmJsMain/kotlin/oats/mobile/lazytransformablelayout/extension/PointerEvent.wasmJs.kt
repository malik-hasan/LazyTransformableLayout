package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.dom.domEventOrNull
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import org.w3c.dom.events.WheelEvent
import kotlin.math.pow

private const val PINCH_ZOOM_MULTIPLIER = 10f

internal actual fun PointerEvent.wheelZoomFactor(scrollY: Float): Float {
    val log2ZoomPerDelta = when ((domEventOrNull as? WheelEvent)?.deltaMode) {
        WheelEvent.DOM_DELTA_LINE -> 0.05f
        WheelEvent.DOM_DELTA_PAGE -> 1f
        else -> 0.002f
    }
    val multiplier = if (keyboardModifiers.isCtrlPressed) PINCH_ZOOM_MULTIPLIER else 1f
    return 2f.pow(-scrollY * log2ZoomPerDelta * multiplier)
}
