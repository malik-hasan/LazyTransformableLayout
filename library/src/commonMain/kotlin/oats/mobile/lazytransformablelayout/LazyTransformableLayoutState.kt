package oats.mobile.lazytransformablelayout

import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.DecayAnimation
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.getVelocityFromNanos
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.extension.clamp
import oats.mobile.lazytransformablelayout.extension.radians
import oats.mobile.lazytransformablelayout.extension.transform
import oats.mobile.lazytransformablelayout.extension.vertices
import oats.mobile.lazytransformablelayout.model.Parallelogram
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
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
 * @param panFlingDecay decay animation spec for panning fling velocity
 * @param zoomRotateFlingDecay decay animation spec for zoom and rotation fling velocity
 */
@Stable
class LazyTransformableLayoutState(
    val layoutBounds: Rect,
    initialOffset: Offset = Offset.Zero,
    val zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
    @FloatRange(from = 0.0, fromInclusive = false) initialScale: Float = 1f,
    val rotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
    initialAngle: Float = 0f,
    private val panFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec(2f),
    private val zoomRotateFlingDecay: DecayAnimationSpec<Pair<Float, Float>> = exponentialDecay(2f)
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
            offset = offset.clamp(panningBounds)
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

    private val layoutBoundVertices = layoutBounds.vertices

    private val transformedLayoutBounds = LongArray(4)

    private val panningBounds by derivedStateOf {
        constraints?.run {
            for (i in 0 until 4) {
                transformedLayoutBounds[i] = Offset(layoutBoundVertices[i])
                    .transform(scale, angle)
                    .packedValue
            }

            var startIndex = 0
            var v0 = Offset(transformedLayoutBounds[0])
            for (i in 1..3) {
                val v = Offset(transformedLayoutBounds[i])
                if (v.x < v0.x || (v.x == v0.x && v.y < v0.y)) {
                    v0 = v
                    startIndex = i
                }
            }

            val v1 = v(startIndex, 1)
            val v2 = v(startIndex, 2)
            val v3 = v(startIndex, 3)

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

    private fun v(startIndex: Int, index: Int) =
        Offset(transformedLayoutBounds[(startIndex + index) % 4])

    internal fun transform(
        zoomFactor: Float,
        rotationDelta: Float,
        panDelta: Offset,
        centroid: Offset,
        overscrollEffect: OverscrollEffect? = null
    ) {
        val scaledPanDelta = panDelta / zoomFactor
        overscrollEffect?.applyToScroll(scaledPanDelta, NestedScrollSource.UserInput) { scrollDelta ->
            transform(
                zoomFactor = zoomFactor,
                rotationDelta = rotationDelta,
                panDelta = scrollDelta,
                centroid = centroid
            )
        } ?: transform(
            zoomFactor = zoomFactor,
            rotationDelta = rotationDelta,
            panDelta = scaledPanDelta,
            centroid = centroid
        )
    }

    private fun transform(
        zoomFactor: Float,
        rotationDelta: Float,
        panDelta: Offset,
        centroid: Offset
    ): Offset {
        val previousScale = scale
        val newScale = (previousScale * zoomFactor).coerceIn(minScaleBound, zoomBounds.endInclusive)

        val previousAngle = angle
        val newAngle = (previousAngle + rotationDelta).coerceIn(rotationBounds)

        val bounds = panningBounds
        val prePanOffset = offset.transform(
            scale = newScale / previousScale,
            angle = newAngle - previousAngle,
            centroid = centroid,
            panningBounds = bounds
        )

        val postPanOffset = prePanOffset.transform(
            offset = panDelta,
            panningBounds = bounds
        )

        scale = newScale
        angle = newAngle
        offset = postPanOffset
        return prePanOffset - postPanOffset
    }

    private val floatPairVectorConverter = TwoWayConverter<Pair<Float, Float>, AnimationVector2D>(
        convertToVector = { AnimationVector2D(it.first, it.second) },
        convertFromVector = { it.v1 to it.v2 }
    )

    internal suspend fun fling(
        initialLogZoomVelocity: Float,
        initialRotationVelocity: Float,
        initialPanVelocity: Velocity,
        centroid: Offset,
        overscrollEffect: OverscrollEffect? = null
    ) = coroutineScope {
        launch {
            overscrollEffect?.applyToFling(initialPanVelocity.copy(y = 0f)) { velocity ->
                flingX(velocity.x)
            } ?: flingX(initialPanVelocity.x)
        }

        launch {
            overscrollEffect?.applyToFling(initialPanVelocity.copy(x = 0f)) { velocity ->
                flingY(velocity.y)
            } ?: flingY(initialPanVelocity.y)
        }

        launch {
            var previousAngle = angle
            var previousScale = scale
            Animatable(
                initialValue = previousAngle to ln(previousScale),
                typeConverter = floatPairVectorConverter
            ).run {
                updateBounds(
                    lowerBound = rotationBounds.start to ln(minScaleBound),
                    upperBound = rotationBounds.endInclusive to ln(zoomBounds.endInclusive)
                )

                animateDecay(
                    initialVelocity = initialRotationVelocity to initialLogZoomVelocity,
                    animationSpec = zoomRotateFlingDecay
                ) {
                    val angleValue = value.first
                    angle = angleValue

                    val scaleValue = exp(value.second)
                    scale = scaleValue

                    offset = offset.transform(
                        scale = scaleValue / previousScale,
                        angle = angleValue - previousAngle,
                        centroid = centroid,
                        panningBounds = panningBounds
                    )

                    previousAngle = angleValue
                    previousScale = scaleValue

                    updateBounds(rotationBounds.start to ln(minScaleBound))
                }
            }
        }
    }

    private suspend fun flingX(xVelocity: Float) = Velocity(
        x = flingPan(xVelocity) { delta ->
            offset.copy(x = offset.x + delta)
        },
        y = 0f
    )

    private suspend fun flingY(yVelocity: Float) = Velocity(
        x = 0f,
        y = flingPan(yVelocity) { delta ->
            offset.copy(y = offset.y + delta)
        }
    )

    private suspend fun flingPan(
        initialVelocity: Float,
        applyDelta: (Float) -> Offset
    ): Float {
        val animation = DecayAnimation(
            animationSpec = panFlingDecay,
            initialValue = 0f,
            initialVelocity = initialVelocity
        )

        val startTimeNanos = withFrameNanos { it }
        var currentVelocity: Float
        var previousValue = 0f

        animation.run {
            do {
                val frameTimeNanos = withFrameNanos { it }
                val playTimeNanos = frameTimeNanos - startTimeNanos

                val currentValue = getValueFromNanos(playTimeNanos)
                currentVelocity = getVelocityFromNanos(playTimeNanos)

                val preClampOffset = applyDelta(currentValue - previousValue)
                val postClampOffset = preClampOffset.clamp(panningBounds)
                offset = postClampOffset

                previousValue = currentValue
            } while (postClampOffset == preClampOffset && !isFinishedFromNanos(playTimeNanos))
        }

        return currentVelocity
    }
}
