package oats.mobile.lazytransformablelayout.utility

import androidx.compose.ui.unit.DpOffset

operator fun DpOffset.unaryMinus() = DpOffset(-x, -y)

operator fun DpOffset.div(divisor: Float) = DpOffset(x / divisor, y / divisor)

operator fun DpOffset.times(factor: Float) = DpOffset(x * factor, y * factor)
