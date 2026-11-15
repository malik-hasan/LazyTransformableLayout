package mobile.oats.lazytransformablelayout

import androidx.compose.ui.unit.DpOffset

/**
 * How a [LazyTransformableLayout] appears in the viewport: the point in layout space at the center of the content
 * area, and the rotation and zoom the layout is shown at around it.
 *
 * @property rotationAngle rotation angle of the layout in degrees, clockwise
 * @property zoomScale zoom scale of the layout, where 1 shows the layout at its size in dp
 * @property centerOffset the point in layout space at the center of the content area
 */
data class LayoutTransform(
    val rotationAngle: Float = 0f,
    val zoomScale: Float = 1f,
    val centerOffset: DpOffset = DpOffset.Zero
)
