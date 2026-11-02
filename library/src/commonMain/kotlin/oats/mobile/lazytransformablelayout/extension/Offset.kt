package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import kotlin.math.cos
import kotlin.math.sin

internal fun Offset.nearestPointOnSegment(p: Offset, q: Offset): Offset {
    val segment = q - p
    val lenSq = segment.getDistanceSquared()
    return if (lenSq == 0f) {
        p
    } else p + segment * (((this - p) dot segment) / lenSq).coerceIn(0f, 1f)
}

internal fun Offset.distanceSquared(other: Offset) = (this - other).getDistanceSquared()

internal infix fun Offset.cross(other: Offset) = x * other.y - y * other.x

internal infix fun Offset.dot(other: Offset) = x * other.x + y * other.y

internal fun Offset.transform(
    scale: Float = 1f,
    angle: Float = 0f,
    offset: Offset = Offset.Zero
): Offset {
    val scaled = this * scale
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
    } - offset
}

internal fun Offset.toViewportAxes(scale: Float, angle: Float) = transform(scale, angle)

internal fun Offset.toLayoutAxes(scale: Float, angle: Float) = transform(scale = 1 / scale, angle = -angle)

context(density: Density)
internal fun Offset.toDp() = with(density) {
    DpOffset(x.toDp(), y.toDp())
}
