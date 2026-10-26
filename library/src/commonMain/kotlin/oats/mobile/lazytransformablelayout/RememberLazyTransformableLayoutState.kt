package oats.mobile.lazytransformablelayout

import androidx.annotation.FloatRange
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import oats.mobile.lazytransformablelayout.extension.center

/**
 * Creates and remembers a [LazyTransformableLayoutState] that survives configuration changes
 *
 * @param initialLayoutBounds The bounds of the layout which can be panned into view, in the same space as the items' bounds
 * @param initialRotationBounds min and max angle bounds in degrees
 * @param initialZoomBounds min and max scale bounds
 * @param initialCenterOffset initial point of the layout at the center of the viewport, defaulting to the center of [initialLayoutBounds]
 * @param initialRotationAngle initial rotation angle in degrees
 * @param initialZoomScale initial zoom scale (greater than zero)
 * @param panFlingDecay decay animation spec for panning fling velocity
 * @param rotationFlingDecay decay animation spec for rotation fling velocity in degrees
 * @param zoomFlingDecay decay animation spec for zoom fling velocity in log scale
 */
@Composable
fun rememberLazyTransformableLayoutState(
    initialLayoutBounds: DpRect,
    initialRotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
    initialZoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
    initialCenterOffset: DpOffset = initialLayoutBounds.center,
    initialRotationAngle: Float = 0f,
    @FloatRange(from = 0.0, fromInclusive = false) initialZoomScale: Float = 1f,
    panFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec(),
    rotationFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec(),
    zoomFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec()
): LazyTransformableLayoutState {
    return rememberSaveable(
        saver = LazyTransformableLayoutState.saver(
            panFlingDecay,
            rotationFlingDecay,
            zoomFlingDecay
        )
    ) {
        LazyTransformableLayoutState(
            initialLayoutBounds = initialLayoutBounds,
            initialRotationBounds = initialRotationBounds,
            initialZoomBounds = initialZoomBounds,
            initialCenterOffset = initialCenterOffset,
            initialRotationAngle = initialRotationAngle,
            initialZoomScale = initialZoomScale,
            panFlingDecay = panFlingDecay,
            rotationFlingDecay = rotationFlingDecay,
            zoomFlingDecay = zoomFlingDecay
        )
    }
}
