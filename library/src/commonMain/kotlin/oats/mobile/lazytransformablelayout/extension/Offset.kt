package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import kotlin.math.cos
import kotlin.math.sin

/**
 * Returns the point on the segment from [p] to [q] that is closest to this point
 */
fun Offset.nearestPointOnSegment(p: Offset, q: Offset): Offset {
    val segment = q - p
    val lenSq = segment.getDistanceSquared()
    return if (lenSq == 0f) {
        p
    } else p + segment * (((this - p) dot segment) / lenSq).coerceIn(0f, 1f)
}

/**
 * Returns the squared distance between this point and [other], which avoids a square root when only comparing distances
 */
fun Offset.distanceSquared(other: Offset) = (this - other).getDistanceSquared()

/**
 * Returns the z component of the 3D cross product of this vector and [other]. It is positive when [other]
 * points clockwise of this vector on screen, where y points down, and zero when they are parallel.
 */
infix fun Offset.cross(other: Offset) = x * other.y - y * other.x

/**
 * Returns the dot product of this vector and [other]
 */
infix fun Offset.dot(other: Offset) = x * other.x + y * other.y

/**
 * Scales this point by [scale] and rotates it clockwise by [angle] degrees about [pivot], then subtracts [offset]
 *
 * @param scale factor to scale by
 * @param angle angle to rotate by in degrees, clockwise on screen
 * @param offset amount to subtract after scaling and rotating
 * @param pivot point to scale and rotate about
 */
fun Offset.transform(
    scale: Float = 1f,
    angle: Float = 0f,
    offset: Offset = Offset.Zero,
    pivot: Offset = Offset.Zero
): Offset {
    val scaled = (this - pivot) * scale
    return if (angle == 0f) {
        scaled
    } else {
        val (x, y) = scaled
        val angleRadians = angle.radians
        val cos = cos(angleRadians)
        val sin = sin(angleRadians)
        Offset(
            x = x * cos - y * sin,
            y = x * sin + y * cos
        )
    } + pivot - offset
}

/**
 * Converts this offset from layout space axes to viewport axes by scaling and rotating it, keeping the origin in place.
 * This usually converts a distance or direction, such as a pan delta. To translate a *position* to viewport space,
 * use [oats.mobile.lazytransformablelayout.LazyTransformableLayoutState.layoutToViewport]
 *
 * @param scale zoom scale
 * @param angle rotation angle in degrees
 */
fun Offset.toViewportAxes(scale: Float, angle: Float) = transform(scale, angle)

/**
 * Converts this offset from viewport axes to layout space axes by undoing the scale and rotation, keeping the origin
 * in place. This usually converts a distance or direction, such as a pan delta. To translate a *position* to layout
 * space, use [oats.mobile.lazytransformablelayout.LazyTransformableLayoutState.viewportToLayout]
 *
 * @param scale zoom scale
 * @param angle rotation angle in degrees
 */
fun Offset.toLayoutAxes(scale: Float, angle: Float) = transform(scale = 1 / scale, angle = -angle)

/**
 * Converts this offset from px to dp using [density]
 */
context(density: Density)
fun Offset.toDp() = with(density) {
    DpOffset(x.toDp(), y.toDp())
}
