package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.input.pointer.PointerEvent
import kotlin.math.pow

private const val ZOOM_FACTOR_PER_NOTCH = 1.1f

internal fun notchWheelZoomFactor(scrollY: Float) = ZOOM_FACTOR_PER_NOTCH.pow(-scrollY)

internal expect fun PointerEvent.wheelZoomFactor(scrollY: Float): Float
