package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.unit.DpOffset

/**
 * The point that stays fixed on screen while a [oats.mobile.lazytransformablelayout.LazyTransformableLayoutState]
 * rotates and zooms
 */
sealed class Pivot {

    context(geometry: Geometry)
    internal abstract fun viewportPosition(angle: Float, scale: Float, center: DpOffset): Offset

    /**
     * A point in the viewport
     *
     * @property position position in px from the viewport's top-left corner, like a pointer position.
     * [Offset.Unspecified] falls back to the center of the content area
     */
    class Viewport(val position: Offset) : Pivot() {
        context(geometry: Geometry)
        override fun viewportPosition(angle: Float, scale: Float, center: DpOffset) =
            position.takeIf { it.isSpecified } ?: geometry.contentCenter
    }

    /**
     * A point in layout space, which stays where it currently is on screen
     *
     * @property position position in layout space, in dp
     */
    class Layout(val position: DpOffset) : Pivot() {
        context(geometry: Geometry)
        override fun viewportPosition(angle: Float, scale: Float, center: DpOffset) =
            geometry.layoutToViewport(position, angle, scale, center)
    }

    /**
     * The center of the content area
     */
    data object ContentCenter : Pivot() {
        context(geometry: Geometry)
        override fun viewportPosition(angle: Float, scale: Float, center: DpOffset) =
            geometry.contentCenter
    }
}
