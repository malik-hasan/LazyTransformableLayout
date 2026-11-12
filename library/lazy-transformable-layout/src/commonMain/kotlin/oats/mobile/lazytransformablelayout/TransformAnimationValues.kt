package oats.mobile.lazytransformablelayout

import androidx.compose.ui.unit.DpOffset

/**
 * The values a [LazyTransformableLayoutState] animation interpolates,
 * for building its animation spec, such as keyframes or a visibility threshold.
 *
 * @property rotationAngle rotation angle in degrees
 * @property logZoomScale natural logarithm of the zoom scale, so that equal steps zoom by equal factors
 * @property layoutPivot the pivot passed to the animate function, in layout space
 */
data class TransformAnimationValues(
    val rotationAngle: Float = 0f,
    val logZoomScale: Float = 0f,
    val layoutPivot: DpOffset = DpOffset.Zero
)
