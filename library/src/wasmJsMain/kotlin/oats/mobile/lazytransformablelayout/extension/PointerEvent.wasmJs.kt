package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.dom.domEventOrNull
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import org.w3c.dom.events.WheelEvent
import kotlin.math.pow

private const val LOG2_ZOOM_PER_SCROLL_PIXEL = 0.006f
private const val LOG2_ZOOM_PER_PINCH_PIXEL = 0.02f
private const val LOG2_ZOOM_PER_LINE = 0.05f
private const val LOG2_ZOOM_PER_PAGE = 1f

internal actual fun PointerEvent.wheelZoomFactor(scrollY: Float): Float {
    val log2ZoomPerDelta = when ((domEventOrNull as? WheelEvent)?.deltaMode) {
        WheelEvent.DOM_DELTA_LINE -> LOG2_ZOOM_PER_LINE
        WheelEvent.DOM_DELTA_PAGE -> LOG2_ZOOM_PER_PAGE
        else -> if (keyboardModifiers.isCtrlPressed) LOG2_ZOOM_PER_PINCH_PIXEL else LOG2_ZOOM_PER_SCROLL_PIXEL
    }
    return 2f.pow(-scrollY * log2ZoomPerDelta)
}
