package oats.mobile.lazytransformablelayout.utility

import androidx.compose.ui.geometry.Offset
import oats.mobile.lazytransformablelayout.model.Parallelogram
import kotlin.math.cos
import kotlin.math.sin

fun Offset.rotate(angle: Float): Offset {
    val angleRadians = angle.radians
    val cos = cos(angleRadians)
    val sin = sin(angleRadians)
    return Offset(
        x = x * cos - y * sin,
        y = x * sin + y * cos
    )
}

internal fun Offset.clampToBounds(bounds: Parallelogram?) = bounds?.run {
    val ux = b.x - a.x
    val uy = b.y - a.y
    val vx = d.x - a.x
    val vy = d.y - a.y

    val ex = x - a.x
    val ey = y - a.y

    val det = ux * vy - uy * vx

    val s = ((ex * vy - ey * vx) / det).coerceIn(0f, 1f)
    val t = ((ux * ey - uy * ex) / det).coerceIn(0f, 1f)

    Offset(
        x = a.x + s * ux + t * vx,
        y = a.y + s * uy + t * vy
    )
} ?: this
