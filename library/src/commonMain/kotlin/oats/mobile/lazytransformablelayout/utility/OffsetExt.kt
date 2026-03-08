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

//internal fun Offset.clampToBounds(bounds: Parallelogram?) = bounds?.run {
//    val u = b - a
//    val v = d - a
//    val w = this@clampToBounds - a
//
//    val determinant = u cross v
//    if (abs(determinant) < 1e-4f) {
//        val end = maxOf(u, v, compareBy { it.getDistanceSquared() })
//        val lenSq = end.getDistanceSquared()
//        if (lenSq == 0f) {
//            a
//        } else a + end * ((w.x * end.x + w.y * end.y) / lenSq).coerceIn(0f, 1f)
//    } else {
//        val s = ((w cross v) / determinant).coerceIn(0f, 1f)
//        val t = ((u cross w) / determinant).coerceIn(0f, 1f)
//
//        a + u * s + v * t
//    }
//} ?: this

private fun nearestPointOnSegment(p: Offset, p1: Offset, p2: Offset): Offset {
    val seg = p2 - p1
    val lenSq = seg.getDistanceSquared()
    if (lenSq == 0f) return p1
    val t = ((p - p1).x * seg.x + (p - p1).y * seg.y) / lenSq
    return p1 + seg * t.coerceIn(0f, 1f)
}

internal fun Offset.clampToBounds(bounds: Parallelogram?) = bounds?.run {
    val u = b - a
    val v = d - a
    val c = b + d - a  // 4th corner
    val w = this@clampToBounds - a
    val determinant = u cross v

    if (abs(determinant) < 1e-4f) {
        val end = maxOf(u, v, compareBy { it.getDistanceSquared() })
        val lenSq = end.getDistanceSquared()
        if (lenSq == 0f) a
        else a + end * ((w.x * end.x + w.y * end.y) / lenSq).coerceIn(0f, 1f)
    } else {
        val s = (w cross v) / determinant
        val t = (u cross w) / determinant

        if (s in 0f..1f && t in 0f..1f) {
            this@clampToBounds  // already inside
        } else {
            listOf(
                nearestPointOnSegment(this@clampToBounds, a, b),
                nearestPointOnSegment(this@clampToBounds, b, c),
                nearestPointOnSegment(this@clampToBounds, d, c),
                nearestPointOnSegment(this@clampToBounds, a, d)
            ).minBy { (it - this@clampToBounds).getDistanceSquared() }
        }
    }
} ?: this

infix fun Offset.cross(other: Offset) = x * other.y - y * other.x
