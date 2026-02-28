package oats.mobile.lazytransformablelayout

import androidx.annotation.FloatRange
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastMap
import oats.mobile.lazytransformablelayout.utility.clampToBounds
import oats.mobile.lazytransformablelayout.utility.rotate

/**
 * The state of the LazyTransformableLayout
 *
 * @param layoutBounds The bounds of the layout which can be panned into view
 * @param initialOffset initial offset of the top left corner of the viewport relative to the layoutBounds
 * @param initialScale initial zoom scale (greater than zero)
 * @param zoomBounds min and max scale bounds
 * @param initialAngle initial rotation angle in degrees
 * @param rotationBounds min and max angle bounds in degrees
 * @param flingAnimationSpec decay animation spec for panning fling velocity
 */
@Stable
class LazyTransformableLayoutState(
    val layoutBounds: Rect,
    initialOffset: Offset = Offset.Zero,
    @FloatRange(from = 0.0, fromInclusive = false) initialScale: Float = 1f,
    val zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
    initialAngle: Float = 0f,
    val rotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
    private val flingAnimationSpec: DecayAnimationSpec<Float> = exponentialDecay(1.5f)
) {
    init {
        require(initialOffset.x >= layoutBounds.left
            && initialOffset.x <= layoutBounds.right
            && initialOffset.y >= layoutBounds.top
            && initialOffset.y <= layoutBounds.bottom
        ) { "initialViewportOffset ($initialOffset) must be within layoutBounds: ($layoutBounds)" }

        require(zoomBounds.start > 0f) {
            "zoomBounds must be positive. Got: $zoomBounds"
        }

        zoomBounds.run {
            require(endInclusive >= start) {
                "max zoom bound ($endInclusive) must be greater than or equal to min zoom bounds ($start)."
            }
        }

        require(initialScale in zoomBounds) {
            "initialScale ($initialScale) must be within zoomBounds ($zoomBounds)."
        }

        rotationBounds.run {
            require(endInclusive >= start) {
                "max rotation bound ($endInclusive) must be greater than or equal to min zoom bounds ($start)."
            }
        }

        require(initialAngle in rotationBounds) {
            "initialRotation ($initialAngle) must be within rotationBounds ($rotationBounds)."
        }
    }

    var scale by mutableFloatStateOf(initialScale)
        private set

    var angle by mutableFloatStateOf(initialAngle)
        private set

    var offset by mutableStateOf(initialOffset)
        private set

    private var constraints by mutableStateOf<IntSize?>(null)

    internal fun passConstraints(incomingConstraints: Constraints) {
        val previousConstraints = constraints
        constraints = incomingConstraints.run { IntSize(maxWidth, maxHeight) }
        if (previousConstraints == null) {
            offset = offset.clampToBounds(panningBounds)
            scale = scale.coerceAtLeast(minScaleBound)
        }
    }

    private val minScaleBound by derivedStateOf {
        val lowerZoomBound = zoomBounds.start
        constraints?.run {
            maxOf(
                lowerZoomBound,
                width / layoutBounds.width,
                height / layoutBounds.height
            )
        } ?: lowerZoomBound
    }

    private val panningBounds by derivedStateOf {
        constraints?.run {
            val transformedLayoutBounds = layoutBounds
                .run { listOf(topLeft, topRight, bottomRight, bottomLeft) }
                .fastMap { (it * scale).rotate(angle) }

            val startIndex = transformedLayoutBounds.withIndex().minWith(
                compareBy({ it.value.x }, { it.value.y })
            ).index

            fun v(i: Int) = transformedLayoutBounds[(startIndex + i) % 4]
            val v1 = transformedLayoutBounds[startIndex]
            val v2 = v(1)
            val v3 = v(2)
            val v4 = v(3)

            val left = v1.x
            val top = v2.y
            val right = v3.x - width
            val bottom = v4.y - height

            listOf(
                Offset(left, (v1.y - height / 2).coerceIn(top, bottom)),
                Offset((v2.x - width / 2).coerceIn(left, right), top),
                Offset(right, (v3.y - height / 2).coerceIn(top, bottom)),
                Offset((v4.x - width / 2).coerceIn(left, right), bottom)
            )
        }
    }

    internal fun transform(zoomFactor: Float, rotationDelta: Float, panDelta: Offset, centroid: Offset): Offset {
        scale = (scale * zoomFactor).coerceIn(minScaleBound, zoomBounds.endInclusive)
        angle = (angle + rotationDelta).coerceIn(rotationBounds)

        val previousOffset = offset
        val offsetCentroid = centroid + offset
        offset = (offset - panDelta - offsetCentroid + (offsetCentroid * zoomFactor).rotate(rotationDelta))
            .clampToBounds(panningBounds)
        return offset - previousOffset
    }

//
//    fun panToOffset(newOffset: DpOffset) {
//        offset = (-newOffset).coerceInBounds(topLeftPanningBounds)
//    }
//
//    suspend fun animatePanToOffset(newOffset: DpOffset) {
//        Animatable(offset, DpOffset.VectorConverter).run {
//            with(topLeftPanningBounds) {
//                updateBounds(
//                    lowerBound = DpOffset(left, top),
//                    upperBound = DpOffset(right, bottom)
//                )
//            }
//            animateTo(-newOffset) { offset = value }
//        }
//    }
//
//    private fun coerceScaleInBounds(scale: Float) = scale.fastCoerceIn(minScaleBound, zoomBounds.endInclusive)
//
//    private fun constrainUpperPanningBound(constraint: Dp, upperBound: Dp, lowerBound: Dp) = with(density) {
//        (constraint / scale - upperBound).coerceAtMost(lowerBound)
//    }
//
//    internal suspend fun flingX(velocity: Velocity) =
//        Velocity(
//            x = fling(
//                initialVelocity = velocity.x,
//                initialValue = offset.x,
//                minBound = topLeftPanningBounds.left,
//                maxBound = topLeftPanningBounds.right,
//            ) { offset.copy(x = it) },
//            y = 0f
//        )
//
//    internal suspend fun flingY(velocity: Velocity) =
//        Velocity(
//            x = 0f,
//            y = fling(
//                initialVelocity = velocity.y,
//                initialValue = offset.y,
//                minBound = topLeftPanningBounds.top,
//                maxBound = topLeftPanningBounds.bottom,
//            ) { offset.copy(y = it) }
//        )
//
//    // must use separate float animations instead of DpOffset animation, so that the horizontal fling continues even if it hits the vertical boundary and vice versa
//    private suspend fun fling(
//        initialVelocity: Float,
//        initialValue: Dp,
//        minBound: Dp,
//        maxBound: Dp,
//        updatedOffset: (Dp) -> DpOffset
//    ) = with(density) {
//        initialVelocity - Animatable(initialValue.toPx(), Float.VectorConverter).run {
//            updateBounds(minBound.toPx(), maxBound.toPx())
//            animateDecay(initialVelocity, flingAnimationSpec) {
//                offset = updatedOffset(value.toDp())
//            }
//        }.endState.velocity
//    }
}
