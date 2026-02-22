package oats.mobile.lazytransformablelayout.utility

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

context(density: Density)
fun Offset.toDpOffset() = with(density) {
    DpOffset(x.toDp(), y.toDp())
}

fun Offset.rotate(angle: Float): Offset {
    val angleRadians = angle * PI.toFloat() / 180f
    val cos = cos(angleRadians)
    val sin = sin(angleRadians)
    return Offset(
        x = x * cos - y * sin,
        y = x * sin + y * cos
    )
}
