package mobile.oats.lazytransformablelayout.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import kotlin.math.roundToInt

private const val MAX_CONSTRAINT_LONG_SIDE = 65534

private const val MAX_CONSTRAINT_SHORT_SIDE = 32766

internal class Item(
    val index: Int,
    val constraints: Constraints,
    val position: Offset,
    val downscale: Int,
    val zIndex: Float
)

internal fun Density.downscaleOf(bounds: DpRect): Int {
    val width = bounds.right.toPx() - bounds.left.toPx()
    val height = bounds.bottom.toPx() - bounds.top.toPx()
    var downscale = 1
    while (true) {
        val scaledWidth = (width / downscale).roundToInt()
        val scaledHeight = (height / downscale).roundToInt()
        if (maxOf(scaledWidth, scaledHeight) <= MAX_CONSTRAINT_LONG_SIDE
            && minOf(scaledWidth, scaledHeight) <= MAX_CONSTRAINT_SHORT_SIDE
        ) return downscale
        downscale *= 2
    }
}
