package mobile.oats.lazytransformablelayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.unit.DpOffset
import mobile.oats.lazytransformablelayout.internal.Camera
import mobile.oats.lazytransformablelayout.internal.Geometry

/**
 * The point that stays fixed on screen while a [LazyTransformableLayoutState]
 * rotates and zooms
 */
sealed class Pivot {

    context(geometry: Geometry)
    internal abstract fun viewportPosition(camera: Camera): Offset

    /**
     * A point in the viewport
     *
     * @property position position in px from the viewport's top-left corner, like a pointer position.
     * [Offset.Unspecified] falls back to the center of the content area
     */
    data class Viewport(val position: Offset) : Pivot() {
        context(geometry: Geometry)
        override fun viewportPosition(camera: Camera) =
            position.takeIf { it.isSpecified } ?: geometry.contentCenter
    }

    /**
     * A point in layout space, which stays where it currently is on screen
     *
     * @property position position in layout space, in dp
     */
    data class Layout(val position: DpOffset) : Pivot() {
        context(geometry: Geometry)
        override fun viewportPosition(camera: Camera) =
            geometry.layoutToViewport(position, camera)
    }

    /**
     * The center of the content area
     */
    data object ContentCenter : Pivot() {
        context(geometry: Geometry)
        override fun viewportPosition(camera: Camera) =
            geometry.contentCenter
    }
}
