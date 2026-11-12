package mobile.oats.lazytransformablelayout.extension

import androidx.compose.ui.input.pointer.PointerEvent
import kotlin.math.pow

internal fun notchWheelZoomFactor(scrollY: Float) = 1.1f.pow(-scrollY)

internal expect fun PointerEvent.wheelZoomFactor(scrollY: Float): Float
