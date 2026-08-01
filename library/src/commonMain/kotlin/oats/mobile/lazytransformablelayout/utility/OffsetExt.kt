package oats.mobile.lazytransformablelayout.utility

import androidx.compose.ui.geometry.Offset
import oats.mobile.lazytransformablelayout.model.Parallelogram
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

fun Offset.rotate(angle: Float): Offset {
    if (angle == 0f) return this

    val angleRadians = angle.radians
    val cos = cos(angleRadians)
    val sin = sin(angleRadians)
    return Offset(
        x = x * cos - y * sin,
        y = x * sin + y * cos
    )
}

internal fun Offset.clamp(bounds: Parallelogram?) = bounds?.run {
    val u = top - left
    val v = bottom - left
    val w = this@clamp - left
    val determinant = u cross v

    if (abs(determinant) < 1e-4f) {
        nearestPointOnSegment(left, right)
    } else {
        val s = (w cross v) / determinant
        val t = (u cross w) / determinant

        var clampedPoint = this@clamp

        if (s !in 0f..1f || t !in 0f..1f) {
            val inverseS = 1 - s
            val inverseT = 1 - t

            var clampedPointDistSq = Float.MAX_VALUE

            if (t < 0f || t <= s && t <= inverseS) {
                clampedPoint = nearestPointOnSegment(left, top)
                clampedPointDistSq = distanceSquared(clampedPoint)
            }

            if (s > 1f || s >= t && s >= inverseT) {
                val nearestPoint = nearestPointOnSegment(top, right)
                val nearestPointDistSq = distanceSquared(nearestPoint)
                if (nearestPointDistSq < clampedPointDistSq) {
                    clampedPoint = nearestPoint
                    clampedPointDistSq = nearestPointDistSq
                }
            }

            if (t > 1f || t >= s && t >= inverseS) {
                val nearestPoint = nearestPointOnSegment(right, bottom)
                val nearestPointDistSq = distanceSquared(nearestPoint)
                if (nearestPointDistSq < clampedPointDistSq) {
                    clampedPoint = nearestPoint
                    clampedPointDistSq = nearestPointDistSq
                }
            }

            if (s < 0f || s <= t && s <= inverseT) {
                val nearestPoint = nearestPointOnSegment(bottom, left)
                if (distanceSquared(nearestPoint) < clampedPointDistSq) {
                    clampedPoint = nearestPoint
                }
            }
        }

        clampedPoint
    }
} ?: this

fun Offset.nearestPointOnSegment(p: Offset, q: Offset): Offset {
    val segment = q - p
    val lenSq = segment.getDistanceSquared()
    return if (lenSq == 0f) {
        p
    } else p + segment * (((this - p) dot segment) / lenSq).coerceIn(0f, 1f)
}

fun Offset.distanceSquared(other: Offset) = (this - other).getDistanceSquared()

infix fun Offset.cross(other: Offset) = x * other.y - y * other.x

infix fun Offset.dot(other: Offset) = x * other.x + y * other.y

fun Offset.transform(scale: Float = 1f, angle: Float = 0f, offset: Offset = Offset.Zero) =
    (this * scale).rotate(angle) - offset
