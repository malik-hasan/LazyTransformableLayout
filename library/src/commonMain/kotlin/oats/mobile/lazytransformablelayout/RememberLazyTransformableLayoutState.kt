package oats.mobile.lazytransformablelayout

import androidx.annotation.FloatRange
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect

/**
 * Creates and remembers a [LazyTransformableLayoutState] that survives configuration changes
 *
 * @param layoutBounds The bounds of the layout which can be panned into view, in the same space as the items' bounds
 * @param rotationBounds min and max angle bounds in degrees
 * @param zoomBounds min and max scale bounds
 * @param initialCenter initial point of the layout at the center of the viewport, defaulting to the center of [layoutBounds]
 * @param initialAngle initial rotation angle in degrees
 * @param initialScale initial zoom scale (greater than zero)
 * @param panFlingDecay decay animation spec for panning fling velocity
 * @param rotationFlingDecay decay animation spec for rotation fling velocity in degrees
 * @param zoomFlingDecay decay animation spec for zoom fling velocity in log scale
 */
@Composable
fun rememberLazyTransformableLayoutState(
    layoutBounds: DpRect,
    rotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
    zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
    initialCenter: DpOffset? = null,
    initialAngle: Float = 0f,
    @FloatRange(from = 0.0, fromInclusive = false) initialScale: Float = 1f,
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
            layoutBounds = layoutBounds,
            rotationBounds = rotationBounds,
            zoomBounds = zoomBounds,
            initialCenter = initialCenter,
            initialAngle = initialAngle,
            initialScale = initialScale,
            panFlingDecay = panFlingDecay,
            rotationFlingDecay = rotationFlingDecay,
            zoomFlingDecay = zoomFlingDecay
        )
    }
}
