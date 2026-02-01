package oats.mobile.lazytransformablelayout

import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.util.fastCoerceIn
import oats.mobile.lazytransformablelayout.utility.coerceInBounds
import oats.mobile.lazytransformablelayout.utility.rotateBy
import oats.mobile.lazytransformablelayout.utility.times
import oats.mobile.lazytransformablelayout.utility.unaryMinus

/**
 * The state of the LazyTransformableLayout
 *
 * @param layoutBounds The bounds of the layout which can be panned into view, in which all the items should be placed (and constrained)
 * @property density pixel density is necessary to convert between Px and Dp values for internal calculations
 * @param initialViewportOffset offset of the point within the layout bounds which should be the top left corner of the viewport on first composition
 * @param initialScale initial zoom scale (greater than zero)
 * @param zoomBounds min and max scale bounds
 * @param initialAngle initial rotation angle
 * @param rotationBounds min and max angle bounds
 * @param flingAnimationSpec decay animation spec for panning fling velocity
 */
@Stable
class LazyTransformableLayoutState(
    val layoutBounds: DpRect,
    private val density: Density,
    initialViewportOffset: DpOffset = DpOffset.Zero,
    @FloatRange(from = 0.0, fromInclusive = false) initialScale: Float = 1f,
    val zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
    initialAngle: Float = 0f,
    val rotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
    private val flingAnimationSpec: DecayAnimationSpec<Float> = exponentialDecay(1.5f)
) {
    init {
        require(initialViewportOffset.x >= layoutBounds.left
            && initialViewportOffset.x <= layoutBounds.right
            && initialViewportOffset.y >= layoutBounds.top
            && initialViewportOffset.y <= layoutBounds.bottom
        ) { "initialViewportOffset ($initialViewportOffset) must be within layoutBounds: ($layoutBounds)" }

        require(zoomBounds.start > 0f) {
            "zoomBounds must be positive. Got: $zoomBounds"
        }

        require(zoomBounds.endInclusive >= zoomBounds.start) {
            "max zoom bound (${zoomBounds.endInclusive}) must be greater than or equal to min zoom bounds (${zoomBounds.start})."
        }

        require(initialScale in zoomBounds) {
            "initialScale ($initialScale) must be within zoomBounds ($zoomBounds)."
        }

        require(rotationBounds.endInclusive >= rotationBounds.start) {
            "max rotation bound (${rotationBounds.endInclusive}) must be greater than or equal to min zoom bounds (${rotationBounds.start})."
        }

        require(initialAngle in rotationBounds) {
            "initialRotation ($initialAngle) must be within rotationBounds ($rotationBounds)."
        }
    }

    val viewportOffset get() = -offset

    fun panToOffset(newOffset: DpOffset) {
        offset = (-newOffset).coerceInBounds(topLeftPanningBounds)
    }

    suspend fun animatePanToOffset(newOffset: DpOffset) {
        Animatable(offset, DpOffset.VectorConverter).run {
            with(topLeftPanningBounds) {
                updateBounds(
                    lowerBound = DpOffset(left, top),
                    upperBound = DpOffset(right, bottom)
                )
            }
            animateTo(-newOffset) { offset = value }
        }
    }

    internal fun transform(zoomFactor: Float, rotationDelta: Float, panDelta: DpOffset, centroid: DpOffset): DpOffset {
        scale *= zoomFactor
        angle += rotationDelta

        val previousOffset = offset
        val layoutCentroid = centroid - offset
        offset += panDelta + layoutCentroid - (layoutCentroid * zoomFactor).rotateBy(rotationDelta)
        return offset - previousOffset
    }

    internal fun passConstraints(incomingConstraints: Constraints) = with(density) {
        constraints = DpSize(
            width = incomingConstraints.maxWidth.toDp(),
            height = incomingConstraints.maxHeight.toDp()
        )
    }

    private var constraints by mutableStateOf(DpSize.Zero)

    private val minScaleBound by derivedStateOf {
        maxOf(
            zoomBounds.start,
            constraints.width / layoutBounds.width,
            constraints.height / layoutBounds.height
        )
    }

    private fun coerceScaleInBounds(scale: Float) = scale.fastCoerceIn(minScaleBound, zoomBounds.endInclusive)

    var scale by mutableFloatStateOf(initialScale)
        private set

    var angle by mutableFloatStateOf(initialAngle)
        private set

    internal val topLeftPanningBounds by derivedStateOf {
        with(layoutBounds) {
            DpRect(
                left = constrainUpperPanningBound(constraints.width, right, left),
                top = constrainUpperPanningBound(constraints.height, bottom, top),
                right = left,
                bottom = top
            )
        }
    }

    private fun constrainUpperPanningBound(constraint: Dp, upperBound: Dp, lowerBound: Dp) = with(density) {
        (constraint / scale - upperBound).coerceAtMost(lowerBound)
    }

    internal var offset by mutableStateOf((-initialViewportOffset).coerceInBounds(topLeftPanningBounds))
        private set

    internal suspend fun flingX(velocity: Velocity) =
        Velocity(
            x = fling(
                initialVelocity = velocity.x,
                initialValue = offset.x,
                minBound = topLeftPanningBounds.left,
                maxBound = topLeftPanningBounds.right,
            ) { offset.copy(x = it) },
            y = 0f
        )

    internal suspend fun flingY(velocity: Velocity) =
        Velocity(
            x = 0f,
            y = fling(
                initialVelocity = velocity.y,
                initialValue = offset.y,
                minBound = topLeftPanningBounds.top,
                maxBound = topLeftPanningBounds.bottom,
            ) { offset.copy(y = it) }
        )

    // must use separate float animations instead of DpOffset animation, so that the horizontal fling continues even if it hits the vertical boundary and vice versa
    private suspend fun fling(
        initialVelocity: Float,
        initialValue: Dp,
        minBound: Dp,
        maxBound: Dp,
        updatedOffset: (Dp) -> DpOffset
    ) = with(density) {
        initialVelocity - Animatable(initialValue.toPx(), Float.VectorConverter).run {
            updateBounds(minBound.toPx(), maxBound.toPx())
            animateDecay(initialVelocity, flingAnimationSpec) {
                offset = updatedOffset(value.toDp())
            }
        }.endState.velocity
    }
}
