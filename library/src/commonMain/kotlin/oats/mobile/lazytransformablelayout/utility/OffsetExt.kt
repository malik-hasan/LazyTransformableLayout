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
    val u = top - left
    val v = bottom - left
    val w = this@clampToBounds - left
    val determinant = u cross v

    if (abs(determinant) < 1e-4f) {
        val ac = right - left
        val lenSq = ac.getDistanceSquared()
        if (lenSq == 0f) {
            left
        } else left + ac * ((w dot ac) / lenSq).coerceIn(0f, 1f)
    } else {
        val s = (w cross v) / determinant
        val t = (u cross w) / determinant

        if (s in 0f..1f && t in 0f..1f) {
            this@clampToBounds
        } else {
            val inverseS = 1 - s
            val inverseT = 1 - t
            buildList {
                if (t < 0f || t <= s && t <= inverseS) add(nearestPointOnSegment(left, top))
                if (s > 1f || s >= t && s >= inverseT) add(nearestPointOnSegment(top, right))
                if (t > 1f || t >= s && t >= inverseS) add(nearestPointOnSegment(right, bottom))
                if (s < 0f || s <= t && s <= inverseT) add(nearestPointOnSegment(bottom, left))
            }.minBy { (it - this@clampToBounds).getDistanceSquared() }
        }
    }
} ?: this

fun Offset.nearestPointOnSegment(p: Offset, q: Offset): Offset {
    val segment = q - p
    val lenSq = segment.getDistanceSquared()
    return if (lenSq == 0f) {
        p
    } else p + segment * (((this - p) dot segment) / lenSq).coerceIn(0f, 1f)
}

infix fun Offset.cross(other: Offset) = x * other.y - y * other.x

infix fun Offset.dot(other: Offset) = x * other.x + y * other.y
