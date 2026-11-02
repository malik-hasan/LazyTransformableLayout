package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import oats.mobile.lazytransformablelayout.extension.radians
import oats.mobile.lazytransformablelayout.extension.toDp
import oats.mobile.lazytransformablelayout.extension.toLayoutAxes
import oats.mobile.lazytransformablelayout.extension.toPx
import oats.mobile.lazytransformablelayout.extension.toViewportAxes
import oats.mobile.lazytransformablelayout.extension.vertices
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

internal data class Geometry(
    override val density: Float,
    override val fontScale: Float,
    val viewportSize: IntSize,
    val contentBounds: Rect
): Density {
    val contentCenter
        get() = contentBounds.center

    fun boundsFitScale(bounds: DpRect, rotationAngle: Float, margin: Dp): Float {
        val angleRadians = rotationAngle.radians
        val cos = abs(cos(angleRadians))
        val sin = abs(sin(angleRadians))
        val (width, height) = bounds.toRect().size
        val totalMargin = 2 * margin.toPx()
        return minOf(
            (contentBounds.width - totalMargin) / (width * cos + height * sin),
            (contentBounds.height - totalMargin) / (width * sin + height * cos)
        )
    }

    fun viewportBounds(buffer: Float, rotationAngle: Float, zoomScale: Float, center: Offset) = Parallelogram(
        viewportSize.toSize().toRect()
            .inflate(buffer)
            .vertices.map {
                viewportToLayoutPx(it, rotationAngle, zoomScale, center)
            }
    )

    fun scaleBoundsAt(
        layoutBounds: DpRect,
        zoomBounds: ClosedFloatingPointRange<Float>,
        rotationAngle: Float
    ): ClosedFloatingPointRange<Float> {
        val layoutBounds = layoutBounds.toRect()
        val angleRadians = rotationAngle.radians
        val cos = abs(cos(angleRadians))
        val sin = abs(sin(angleRadians))
        val start = maxOf(
            zoomBounds.start,
            contentBounds.width / layoutBounds.run { width * cos + height * sin },
            contentBounds.height / layoutBounds.run { width * sin + height * cos }
        )
        return start..maxOf(start, zoomBounds.endInclusive)
    }

    fun panningBoundsAt(layoutBounds: DpRect, rotationAngle: Float, zoomScale: Float): Parallelogram {
        val vertices = layoutBounds.toRect().vertices
        val startIndex = (-floor(rotationAngle / 90f).toInt() - 1).mod(4)
        fun vertex(index: Int) = vertices[(startIndex + index) % 4].toViewportAxes(zoomScale, rotationAngle)

        val leftmost = vertex(0)
        val topmost = vertex(1)
        val rightmost = vertex(2)
        val bottommost = vertex(3)

        val (width, height) = contentBounds.size

        val left = leftmost.x
        val top = topmost.y
        val right = (rightmost.x - width).coerceAtLeast(left)
        val bottom = (bottommost.y - height).coerceAtLeast(top)

        fun corner(x: Float, y: Float) =
            Offset(x + width / 2, y + height / 2).toLayoutAxes(zoomScale, rotationAngle)

        return Parallelogram(
            corner(left, (leftmost.y - height / 2).coerceIn(top, bottom)),
            corner((topmost.x - width / 2).coerceIn(left, right), top),
            corner(right, (rightmost.y - height / 2).coerceIn(top, bottom)),
            corner((bottommost.x - width / 2).coerceIn(left, right), bottom)
        )
    }

    fun viewportToLayoutPx(viewportPosition: Offset, angle: Float, scale: Float, center: Offset) =
        center + (viewportPosition - contentCenter).toLayoutAxes(scale, angle)

    fun viewportToLayout(viewportPosition: Offset, angle: Float, scale: Float, center: DpOffset) =
        viewportToLayoutPx(viewportPosition, angle, scale, center.toPx()).toDp()

    fun layoutToViewport(layoutPosition: DpOffset, angle: Float, scale: Float, center: DpOffset) =
        (layoutPosition - center).toPx().toViewportAxes(scale, angle) + contentCenter

    fun centerFromLayoutPivot(viewportPivot: Offset, angle: Float, scale: Float, layoutPivot: Offset) =
        layoutPivot - (viewportPivot - contentCenter).toLayoutAxes(scale, angle)

    fun centerPreservingPivot(
        viewportPivot: Offset,
        angle: Float,
        scale: Float,
        fromViewportPivot: Offset,
        fromAngle: Float,
        fromScale: Float,
        fromCenter: Offset
    ) = centerFromLayoutPivot(
        viewportPivot = viewportPivot,
        angle = angle,
        scale = scale,
        layoutPivot = viewportToLayoutPx(
            viewportPosition = fromViewportPivot,
            angle = fromAngle,
            scale = fromScale,
            center = fromCenter
        )
    )
}
