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
import co.touchlab.kermit.Logger
import oats.mobile.lazytransformablelayout.utility.rotate

/**
 * The state of the LazyTransformableLayout
 *
 * @param layoutBounds The bounds of the layout which can be panned into view, in which all the items should be placed (and constrained)
 * @param initialOffset offset of the point within the layout bounds which should be the top left corner of the viewport on first composition
 * @param initialScale initial zoom scale (greater than zero)
 * @param zoomBounds min and max scale bounds
 * @param initialAngle initial rotation angle
 * @param rotationBounds min and max angle bounds
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

    internal fun passConstraints(incomingConstraints: Constraints) {
        constraints = incomingConstraints.run { IntSize(maxWidth, maxHeight) }
    }

    private var constraints by mutableStateOf(IntSize.Zero)

    private val minScaleBound by derivedStateOf {
        maxOf(
            zoomBounds.start,
            constraints.width / layoutBounds.width,
            constraints.height / layoutBounds.height
        )
    }

    var scale by mutableFloatStateOf(initialScale.coerceAtLeast(minScaleBound))
        private set

    var angle by mutableFloatStateOf(initialAngle)
        private set

    private fun getPanningBounds(): List<Offset> {
        val transformedLayoutBounds = layoutBounds.vertices.map { (it * scale).rotate(angle) }
        val startIndex = transformedLayoutBounds.withIndex().minWith(
            compareBy({ it.value.y }, { it.value.x })
        ).index

        fun v(i: Int) = transformedLayoutBounds[(startIndex + i) % 4]

        val v1 = v(0)
        val v2 = v(1)
        val v3 = v(2)
        val v4 = v(3)

        val top = v1.y
        val right = v2.x
        val bottom = v3.y
        val left = v4.x

        return listOf(
            Offset((v1.x - constraints.width / 2).coerceIn(left, right - constraints.width), v1.y),
            Offset(v2.x - constraints.width, (v2.y - constraints.height / 2).coerceIn(top, bottom - constraints.height)),
            Offset((v3.x - constraints.width / 2).coerceIn(left, right - constraints.width), v3.y - constraints.height),
            Offset(v4.x, (v4.y - constraints.height / 2).coerceIn(top, bottom - constraints.height)),
        ).also { Logger.d("BOUNDS: $it") }
    }

    internal var offset by mutableStateOf((-initialOffset).coerceInPanningBounds())
        private set

    internal fun transform(zoomFactor: Float, rotationDelta: Float, panDelta: Offset, centroid: Offset): Offset {
        scale = (scale * zoomFactor).coerceIn(minScaleBound, zoomBounds.endInclusive)
        angle = (angle + rotationDelta).coerceIn(rotationBounds)

        val previousOffset = offset
        val offsetCentroid = centroid + offset
        offset = (offset - panDelta - offsetCentroid + (offsetCentroid * zoomFactor).rotate(rotationDelta))//.coerceInPanningBounds()
        return offset - previousOffset
    }

    fun Offset.coerceInPanningBounds(): Offset {
        val transformed = (this * scale).rotate(angle)

        val bounds = getPanningBounds()

        // Get the two edge directions of the parallelogram
        val axisX = (bounds[1] - bounds[0]).let { it / it.getDistance() }
        val axisY = (bounds[3] - bounds[0]).let { it / it.getDistance() }

        val origin = bounds[0]
        val projsX = bounds.map { (it - origin).dot(axisX) }
        val projsY = bounds.map { (it - origin).dot(axisY) }

        val relative = transformed - origin
        val clampedX = relative.dot(axisX).coerceIn(projsX.min(), projsX.max())
        val clampedY = relative.dot(axisY).coerceIn(projsY.min(), projsY.max())

        val clamped = origin + axisX * clampedX + axisY * clampedY
        return clamped.rotate(-angle) / scale
    }

    fun Offset.dot(other: Offset) = x * other.x + y * other.y




//    val viewportOffset get() = -offset
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

val Rect.vertices get() = listOf(topLeft, topRight, bottomRight, bottomLeft)
