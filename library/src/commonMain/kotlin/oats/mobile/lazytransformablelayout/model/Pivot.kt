package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpOffset
import oats.mobile.lazytransformablelayout.model.Viewport as ViewportModel

/**
 * The point that stays fixed on screen while a [oats.mobile.lazytransformablelayout.LazyTransformableLayoutState]
 * rotates and zooms
 */
sealed class Pivot {

    context(viewport: ViewportModel)
    internal abstract fun viewportPosition(angle: Float, scale: Float, center: DpOffset): Offset

    /**
     * A point in the viewport
     *
     * @property position position in px from the viewport's top-left corner, like a pointer position
     */
    data class Viewport(val position: Offset) : Pivot() {
        context(viewport: ViewportModel)
        override fun viewportPosition(angle: Float, scale: Float, center: DpOffset) = position
    }

    /**
     * A point in layout space, which stays where it currently is on screen
     *
     * @property position position in layout space, in dp
     */
    data class Layout(val position: DpOffset) : Pivot() {
        context(viewport: ViewportModel)
        override fun viewportPosition(angle: Float, scale: Float, center: DpOffset) =
            viewport.layoutToViewport(position, angle, scale, center)
    }

    /**
     * The center of the content area
     */
    data object ContentCenter : Pivot() {
        context(viewport: ViewportModel)
        override fun viewportPosition(angle: Float, scale: Float, center: DpOffset) =
            viewport.contentCenter
    }
}
