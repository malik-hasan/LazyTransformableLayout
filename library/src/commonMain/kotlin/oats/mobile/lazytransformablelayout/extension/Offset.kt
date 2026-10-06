package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

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

fun Offset.transform(
    scale: Float = 1f,
    angle: Float = 0f,
    offset: Offset = Offset.Zero,
    centroid: Offset = Offset.Zero
): Offset {
    val offsetCentroid = this + centroid
    return (
        this - offset - offsetCentroid + (
            offsetCentroid * scale
        ).let { scaledOffsetCentroid ->
            if (angle == 0f) {
                scaledOffsetCentroid
            } else {
                val (x, y) = scaledOffsetCentroid
                val angleRadians = angle.radians
                val cos = cos(angleRadians)
                val sin = sin(angleRadians)
                Offset(
                    x = x * cos - y * sin,
                    y = x * sin + y * cos
                )
            }
        }
    )
}
