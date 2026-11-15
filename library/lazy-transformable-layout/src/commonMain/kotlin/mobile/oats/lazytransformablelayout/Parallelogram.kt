package mobile.oats.lazytransformablelayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastForEach
import mobile.oats.lazytransformablelayout.extension.cross
import mobile.oats.lazytransformablelayout.extension.distanceSquared
import mobile.oats.lazytransformablelayout.extension.nearestPointOnSegment
import mobile.oats.lazytransformablelayout.extension.toDp
import kotlin.math.abs

/**
 * A parallelogram in layout space, such as the viewport, which is a rectangle rotated relative to layout space
 * when the layout is rotated
 */
class Parallelogram internal constructor(
    internal val vertices: List<Offset>
) {

    internal constructor(p0: Offset, p1: Offset, p2: Offset, p3: Offset) : this(listOf(p0, p1, p2, p3))

    internal operator fun get(index: Int) = vertices[index]

    /**
     * The corners in layout space, in order around the parallelogram. For the viewport they start at its top left
     * and continue clockwise on screen (top left, top right, bottom right, bottom left)
     *
     * @param density The density of the layout
     */
    context(density: Density)
    val corners: List<DpOffset>
        get() = vertices.map { it.toDp() }

    /**
     * Checks whether any part of [rect] is inside this parallelogram
     *
     * @param density The density of the layout
     * @param rect A rectangle in layout space
     */
    context(density: Density)
    fun intersects(rect: DpRect) = with(density) {
        intersects(rect.left.toPx(), rect.top.toPx(), rect.right.toPx(), rect.bottom.toPx())
    }

    private val origin = this[0]
    private val u = this[1] - origin
    private val v = this[3] - origin
    private val determinant = u cross v
    private val center = (origin + this[2]) / 2f

    internal val axisAlignedBoundingBox = run {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        vertices.fastForEach { (x, y) ->
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }

        Rect(
            left = minX,
            top = minY,
            right = maxX,
            bottom = maxY
        )
    }

    internal fun intersects(rect: Rect) = rect.run {
        intersects(left, top, right, bottom)
    }

    internal fun intersects(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        if (right <= axisAlignedBoundingBox.left
            || left >= axisAlignedBoundingBox.right
            || bottom <= axisAlignedBoundingBox.top
            || top >= axisAlignedBoundingBox.bottom
        ) return false

        val d = Offset((left + right) / 2, (top + bottom) / 2) - center
        val rectHalfWidth = (right - left) / 2
        val rectHalfHeight = (bottom - top) / 2
        val halfDeterminant = abs(determinant) / 2

        return abs(u cross d) <= halfDeterminant + rectHalfWidth * abs(u.y) + rectHalfHeight * abs(u.x)
            && abs(v cross d) <= halfDeterminant + rectHalfWidth * abs(v.y) + rectHalfHeight * abs(v.x)
    }

    internal operator fun contains(other: Parallelogram): Boolean = determinant != 0f
        && other.vertices.fastAll {
            val w = it - origin
            val s = (w cross v) / determinant
            val t = (u cross w) / determinant
            s in 0f..1f && t in 0f..1f
        }

    internal fun clamp(offset: Offset) = if (abs(determinant) < 1e-4f) {
        var maxDistanceSquared = -1f
        var start = origin
        var end = origin
        for (i in 0 until 3) for (j in i + 1 until 4) {
            val distanceSquared = this[i].distanceSquared(this[j])
            if (distanceSquared > maxDistanceSquared) {
                maxDistanceSquared = distanceSquared
                start = this[i]
                end = this[j]
            }
        }
        offset.nearestPointOnSegment(start, end)
    } else {
        val w = offset - origin
        val s = (w cross v) / determinant
        val t = (u cross w) / determinant

        var clampedPoint = offset

        if (s !in 0f..1f || t !in 0f..1f) {
            val inverseS = 1 - s
            val inverseT = 1 - t

            var clampedPointDistSq = Float.POSITIVE_INFINITY

            if (t < 0f || t <= s && t <= inverseS) {
                clampedPoint = offset.nearestPointOnSegment(this[0], this[1])
                clampedPointDistSq = offset.distanceSquared(clampedPoint)
            }

            if (s > 1f || s >= t && s >= inverseT) {
                val nearestPoint = offset.nearestPointOnSegment(this[1], this[2])
                val nearestPointDistSq = offset.distanceSquared(nearestPoint)
                if (nearestPointDistSq < clampedPointDistSq) {
                    clampedPoint = nearestPoint
                    clampedPointDistSq = nearestPointDistSq
                }
            }

            if (t > 1f || t >= s && t >= inverseS) {
                val nearestPoint = offset.nearestPointOnSegment(this[2], this[3])
                val nearestPointDistSq = offset.distanceSquared(nearestPoint)
                if (nearestPointDistSq < clampedPointDistSq) {
                    clampedPoint = nearestPoint
                    clampedPointDistSq = nearestPointDistSq
                }
            }

            if (s < 0f || s <= t && s <= inverseT) {
                val nearestPoint = offset.nearestPointOnSegment(this[3], this[0])
                if (offset.distanceSquared(nearestPoint) < clampedPointDistSq) {
                    clampedPoint = nearestPoint
                }
            }
        }

        clampedPoint
    }
}
