package oats.mobile.lazytransformablelayout.utility

import androidx.compose.ui.geometry.Offset
import oats.mobile.lazytransformablelayout.model.Parallelogram
import kotlin.math.abs
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
    val u = b - a
    val v = d - a
    val w = this@clampToBounds - a

    val determinant = u cross v
    if (abs(determinant) < 1e-4f) {
        val end = maxOf(u, v, compareBy { it.getDistanceSquared() })
        val lenSq = end.getDistanceSquared()
        if (lenSq == 0f) {
            a
        } else a + end * ((w.x * end.x + w.y * end.y) / lenSq).coerceIn(0f, 1f)
    } else {
        val s = ((w cross v) / determinant).coerceIn(0f, 1f)
        val t = ((u cross w) / determinant).coerceIn(0f, 1f)

        a + u * s + v * t
    }
} ?: this

infix fun Offset.cross(other: Offset) = x * other.y - y * other.x
