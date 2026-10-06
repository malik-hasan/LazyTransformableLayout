package oats.mobile.lazytransformablelayout

import androidx.annotation.FloatRange
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

@Composable
fun rememberLazyTransformableLayoutState(
    layoutBounds: Rect,
    rotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
    zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
    initialOffset: Offset = Offset.Zero,
    initialAngle: Float = 0f,
    @FloatRange(from = 0.0, fromInclusive = false) initialScale: Float = 1f,
    panFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec(),
    rotateZoomFlingDecay: DecayAnimationSpec<Pair<Float, Float>> = exponentialDecay()
): LazyTransformableLayoutState {
    return rememberSaveable(
        saver = LazyTransformableLayoutState.saver(
            panFlingDecay,
            rotateZoomFlingDecay
        )
    ) {
        LazyTransformableLayoutState(
            layoutBounds = layoutBounds,
            rotationBounds = rotationBounds,
            zoomBounds = zoomBounds,
            initialOffset = initialOffset,
            initialAngle = initialAngle,
            initialScale = initialScale,
            panFlingDecay = panFlingDecay,
            rotateZoomFlingDecay = rotateZoomFlingDecay
        )
    }
}
