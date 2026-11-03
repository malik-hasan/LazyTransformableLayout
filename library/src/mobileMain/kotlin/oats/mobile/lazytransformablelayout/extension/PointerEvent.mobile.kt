package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.input.pointer.PointerEvent

internal actual fun PointerEvent.wheelZoomFactor(scrollY: Float) = notchWheelZoomFactor(scrollY)
