package mobile.oats.lazytransformablelayout

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import mobile.oats.lazytransformablelayout.extension.toDp
import mobile.oats.lazytransformablelayout.internal.Parallelogram

/**
 * The part of layout space shown in the viewport, a rectangle that is rotated relative to layout space when the
 * layout is rotated
 *
 * @property corners The corners of the viewport in layout space, starting at the viewport's top left and continuing
 * clockwise on screen
 */
class VisibleLayoutArea internal constructor(
    private val area: Parallelogram,
    private val density: Density
) {
    val corners: List<DpOffset> = with(density) { area.map { it.toDp() } }

    /**
     * Checks whether any part of [rect] is in this area
     *
     * @param rect a rectangle in layout space
     */
    fun intersects(rect: DpRect) = with(density) {
        area.intersects(rect.left.toPx(), rect.top.toPx(), rect.right.toPx(), rect.bottom.toPx())
    }
}
