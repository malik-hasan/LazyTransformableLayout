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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastMap
import oats.mobile.lazytransformablelayout.model.Parallelogram
import oats.mobile.lazytransformablelayout.utility.clampToBounds
import oats.mobile.lazytransformablelayout.utility.radians
import oats.mobile.lazytransformablelayout.utility.rotate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The state of the LazyTransformableLayout
 *
 * @param layoutBounds The bounds of the layout which can be panned into view
 * @param initialOffset initial offset of the top left corner of the viewport relative to the layoutBounds
 * @param zoomBounds min and max scale bounds
 * @param initialScale initial zoom scale (greater than zero)
 * @param rotationBounds min and max angle bounds in degrees
 * @param initialAngle initial rotation angle in degrees
 * @param flingAnimationSpec decay animation spec for panning fling velocity
 */
@Stable
class LazyTransformableLayoutState(
    val layoutBounds: Rect,
    initialOffset: Offset = Offset.Zero,
    val zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
    @FloatRange(from = 0.0, fromInclusive = false) initialScale: Float = 1f,
    val rotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
    initialAngle: Float = 0f,
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

    internal fun acceptConstraints(incomingConstraints: Constraints) {
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
            val angleRadians = angle.radians
            val cos = abs(cos(angleRadians))
            val sin = abs(sin(angleRadians))
            maxOf(
                lowerZoomBound,
                width / layoutBounds.run { width * cos + height * sin },
                height / layoutBounds.run { width * sin + height * cos }
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

            val (v0, v1, v2, v3) = transformedLayoutBounds.run {
                drop(startIndex) + take(startIndex)
            }

            val left = v0.x
            val top = v1.y
            val right = (v2.x - width).coerceAtLeast(left)
            val bottom = (v3.y - height).coerceAtLeast(top)

            Parallelogram(
                left = Offset(left, (v0.y - height / 2).coerceIn(top, bottom)),
                top = Offset((v1.x - width / 2).coerceIn(left, right), top),
                right = Offset(right, (v2.y - height / 2).coerceIn(top, bottom)),
                bottom = Offset((v3.x - width / 2).coerceIn(left, right), bottom)
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

    internal suspend fun flingX(velocity: Velocity) =
        Velocity(
            x = fling(
                initialVelocity = velocity.x,
                initialValue = offset.x,
                minBound = panningBounds?.left?.x,
                maxBound = panningBounds?.right?.x,
            ) { offset.copy(x = it) },
            y = 0f
        )

    internal suspend fun flingY(velocity: Velocity) =
        Velocity(
            x = 0f,
            y = fling(
                initialVelocity = velocity.y,
                initialValue = offset.y,
                minBound = panningBounds?.top?.y,
                maxBound = panningBounds?.bottom?.y,
            ) { offset.copy(y = it) }
        )

    // must use separate float animations instead of Offset animation
    // so that the horizontal fling continues even if it hits the vertical boundary and vice versa
    private suspend fun fling(
        initialVelocity: Float,
        initialValue: Float,
        minBound: Float?,
        maxBound: Float?,
        updatedOffset: (Float) -> Offset
    ) = initialVelocity - Animatable(initialValue, Float.VectorConverter).run {
        updateBounds(minBound, maxBound)
        animateDecay(initialVelocity, flingAnimationSpec) {
            offset = updatedOffset(value)
        }
    }.endState.velocity
}
