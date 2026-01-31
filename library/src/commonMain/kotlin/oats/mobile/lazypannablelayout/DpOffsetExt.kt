package oats.mobile.lazypannablelayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

fun DpOffset.rotateBy(angle: Float): DpOffset {
    val angleRadians = angle * PI.toFloat() / 180f
    val cos = cos(angleRadians)
    val sin = sin(angleRadians)
    return DpOffset(
        x = x * cos - y * sin,
        y = x * sin + y * cos
    )
}
