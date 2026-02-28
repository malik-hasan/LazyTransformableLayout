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

fun Offset.clampToBounds(bounds: List<Offset>?): Offset {
    bounds ?: return this

    val a = bounds[0]
    val b = bounds[1]
    val d = bounds[3]

    val ux = b.x - a.x; val uy = b.y - a.y
    val vx = d.x - a.x; val vy = d.y - a.y

    val ex = x - a.x; val ey = y - a.y

    val det = ux * vy - uy * vx
    require(det != 0f) { "Degenerate parallelogram (edges are collinear)" }

    val s = ((ex * vy - ey * vx) / det).coerceIn(0f, 1f)
    val t = ((ux * ey - uy * ex) / det).coerceIn(0f, 1f)

    return Offset(
        x = a.x + s * ux + t * vx,
        y = a.y + s * uy + t * vy
    )
}
