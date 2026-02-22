package oats.mobile.lazytransformablelayout.utility

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect

fun DpOffset.coerceInBounds(bounds: DpRect) = with(bounds) {
    DpOffset(
        x = x.coerceIn(left, right),
        y = y.coerceIn(top, bottom)
    )
}

context(density: Density)
fun DpOffset.toOffset() = with(density) {
    Offset(x.toPx(), y.toPx())
}

operator fun DpOffset.unaryMinus() = DpOffset(-x, -y)

operator fun DpOffset.div(divisor: Float) = DpOffset(x / divisor, y / divisor)

operator fun DpOffset.times(factor: Float) = DpOffset(x * factor, y * factor)
