package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import oats.mobile.lazytransformablelayout.extension.radians
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

    fun viewportToLayout(position: Offset, angle: Float, scale: Float, center: DpOffset) =
        center.toPx() + (position - contentCenter).toLayoutAxes(scale, angle)

    fun layoutToViewport(position: DpOffset, angle: Float, scale: Float, center: DpOffset) =
        (position - center).toPx().toViewportAxes(scale, angle) + contentCenter

    fun centerFromPivot(viewportPivot: Offset, angle: Float, scale: Float, layoutPivot: DpOffset) =
        layoutPivot.toPx() - (viewportPivot - contentCenter).toLayoutAxes(scale, angle)
}
