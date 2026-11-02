package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import oats.mobile.lazytransformablelayout.extension.radians
import oats.mobile.lazytransformablelayout.extension.toDp
import oats.mobile.lazytransformablelayout.extension.toLayoutAxes
import oats.mobile.lazytransformablelayout.extension.toPx
import oats.mobile.lazytransformablelayout.extension.toViewportAxes
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal data class Viewport(
    override val density: Float,
    override val fontScale: Float,
    val size: IntSize,
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

    fun viewportToLayoutPx(viewportPosition: Offset, angle: Float, scale: Float, center: Offset) =
        center + (viewportPosition - contentCenter).toLayoutAxes(scale, angle)

    fun viewportToLayout(viewportPosition: Offset, angle: Float, scale: Float, center: DpOffset) =
        viewportToLayoutPx(viewportPosition, angle, scale, center.toPx()).toDp()

    fun layoutToViewport(layoutPosition: DpOffset, angle: Float, scale: Float, center: DpOffset) =
        (layoutPosition - center).toPx().toViewportAxes(scale, angle) + contentCenter

    fun centerFromLayoutPivot(viewportPivot: Offset, angle: Float, scale: Float, layoutPivot: Offset) =
        layoutPivot - (viewportPivot - contentCenter).toLayoutAxes(scale, angle)

    fun centerFromLayoutPivot(viewportPivot: Offset, angle: Float, scale: Float, layoutPivot: DpOffset) =
        centerFromLayoutPivot(viewportPivot, angle, scale, layoutPivot.toPx())

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
