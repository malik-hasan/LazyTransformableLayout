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
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.extension.radians
import oats.mobile.lazytransformablelayout.extension.transform
import oats.mobile.lazytransformablelayout.extension.vertices
import oats.mobile.lazytransformablelayout.model.Parallelogram
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin

/**
 * The state of the LazyTransformableLayout
 *
 * @param layoutBounds The bounds of the layout which can be panned into view
 * @param rotationBounds min and max angle bounds in degrees
 * @param zoomBounds min and max scale bounds
 * @param initialOffset initial offset of the top left corner of the viewport relative to the layoutBounds
 * @param initialAngle initial rotation angle in degrees
 * @param initialScale initial zoom scale (greater than zero)
 * @param panFlingDecay decay animation spec for panning fling velocity
 * @param rotateZoomFlingDecay decay animation spec for zoom and rotation fling velocity
 */
@Stable
class LazyTransformableLayoutState(
    layoutBounds: Rect,
    rotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
    zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
    initialOffset: Offset = Offset.Zero,
    initialAngle: Float = 0f,
    @FloatRange(from = 0.0, fromInclusive = false) initialScale: Float = 1f,
    private val panFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec(),
    private val rotateZoomFlingDecay: DecayAnimationSpec<Pair<Float, Float>> = exponentialDecay()
) {
    internal var layoutBounds by mutableStateOf(layoutBounds)
        private set

    internal var rotationBounds by mutableStateOf(rotationBounds)
        private set

    internal var zoomBounds by mutableStateOf(zoomBounds)
        private set

    init {
        rotationBounds.run {
            require(!isEmpty()) {
                "max rotation bound ($endInclusive) must be greater than or equal to min rotation bound ($start)."
            }
        }

        zoomBounds.run {
            require(!isEmpty()) {
                "max zoom bound ($endInclusive) must be greater than or equal to min zoom bound ($start)."
            }

            require(start > 0) { "zoomBounds must be positive." }
        }
    }

    private var constraints by mutableStateOf<IntSize?>(null)

    internal fun acceptConstraints(incomingConstraints: Constraints) {
        val newConstraints = incomingConstraints.run { IntSize(maxWidth, maxHeight) }
        if (constraints != newConstraints) {
            constraints = newConstraints
            scale = clampScale(scale)
            offset = clampOffset(offset)
        }
    }

    private var previousCompositionBounds: Pair<Float, Parallelogram>? = null

    internal val compositionBounds by derivedStateOf(referentialEqualityPolicy()) {
        constraints?.run {
            val minItemDimension = 0.5f / scale
            previousCompositionBounds?.takeIf { (previousMinItemDimension, previousBounds) ->
                minItemDimension * 1.19f >= previousMinItemDimension
                    && viewportBounds(128f) in previousBounds
            } ?: (minItemDimension to viewportBounds(256f))
                .also { previousCompositionBounds = it }
        }
    }

    private fun IntSize.viewportBounds(buffer: Float) = toSize().toRect()
        .inflate(buffer)
        .run {
            Parallelogram(
                vertices.map {
                    (it + offset).transform(scale = 1 / scale, angle = -angle)
                }
            )
        }

    private val scaleBounds by derivedStateOf {
        val zoomBounds = this.zoomBounds
        constraints?.run {
            val layoutBounds = this@LazyTransformableLayoutState.layoutBounds
            val angleRadians = angle.radians
            val cos = abs(cos(angleRadians))
            val sin = abs(sin(angleRadians))
            val start = maxOf(
                zoomBounds.start,
                width / layoutBounds.run { width * cos + height * sin },
                height / layoutBounds.run { width * sin + height * cos }
            )
            start..maxOf(start, zoomBounds.endInclusive)
        } ?: zoomBounds
    }

    private val panningBounds by derivedStateOf {
        constraints?.run {
            val startIndex = (-floor(angle / 90f).toInt() - 1).mod(4)
            fun vertex(index: Int) = this@LazyTransformableLayoutState.layoutBounds
                .vertices[(startIndex + index) % 4]
                .transform(scale, angle)

            val leftmost = vertex(0)
            val topmost = vertex(1)
            val rightmost = vertex(2)
            val bottommost = vertex(3)

            val left = leftmost.x
            val top = topmost.y
            val right = (rightmost.x - width).coerceAtLeast(left)
            val bottom = (bottommost.y - height).coerceAtLeast(top)

            Parallelogram(
                Offset(left, (leftmost.y - height / 2).coerceIn(top, bottom)),
                Offset((topmost.x - width / 2).coerceIn(left, right), top),
                Offset(right, (rightmost.y - height / 2).coerceIn(top, bottom)),
                Offset((bottommost.x - width / 2).coerceIn(left, right), bottom)
            )
        }
    }

    fun updateBounds(
        layoutBounds: Rect? = null,
        rotationBounds: ClosedFloatingPointRange<Float>? = null,
        zoomBounds: ClosedFloatingPointRange<Float>? = null
    ) {
        layoutBounds?.let {
            this.layoutBounds = it
        }

        rotationBounds?.let {
            this.rotationBounds = it
            angle = clampAngle(angle)
        }

        zoomBounds?.let {
            this.zoomBounds = it
        }

        if (layoutBounds != null || rotationBounds != null || zoomBounds != null) {
            scale = clampScale(scale)
            offset = clampOffset(offset)
        }
    }

    var offset by mutableStateOf(clampOffset(initialOffset))
        private set

    private fun clampOffset(offset: Offset) = panningBounds?.clamp(offset) ?: offset

    operator fun component1() = offset

    var angle by mutableFloatStateOf(clampAngle(initialAngle))
        private set

    private fun clampAngle(angle: Float) = angle.coerceIn(this.rotationBounds)

    operator fun component2() = angle

    var scale by mutableFloatStateOf(clampScale(initialScale))
        private set

    private fun clampScale(scale: Float) = scale.coerceIn(scaleBounds)

    operator fun component3() = scale

    fun set(angle: Float? = null, scale: Float? = null, offset: Offset? = null): Array<Boolean?> {
        val clampedArray = arrayOfNulls<Boolean>(3)

        angle?.let {
            val clampedAngle = clampAngle(angle)
            this.angle = clampedAngle
            clampedArray[0] = clampedAngle == angle
        }

        if (angle != null || scale != null) {
            val preClampScale = scale ?: this.scale
            val clampedScale = clampScale(preClampScale)
            this.scale = clampedScale
            clampedArray[1] = clampedScale == preClampScale
        }

        if (angle != null || scale != null || offset != null) {
            val preClampOffset = offset ?: this.offset
            val clampedOffset = clampOffset(preClampOffset)
            this.offset = clampedOffset
            clampedArray[2] = clampedOffset == preClampOffset
        }

        return clampedArray
    }

    fun transform(
        rotationDelta: Float,
        zoomFactor: Float,
        panDelta: Offset,
        centroid: Offset,
        overscrollEffect: OverscrollEffect? = null,
        nestedScrollSource: NestedScrollSource = NestedScrollSource.UserInput
    ) {
        val scaledPanDelta = panDelta / zoomFactor
        overscrollEffect?.applyToScroll(scaledPanDelta, nestedScrollSource) { scrollDelta ->
            transform(
                rotationDelta = rotationDelta,
                zoomFactor = zoomFactor,
                panDelta = scrollDelta,
                centroid = centroid
            )
        } ?: transform(
            rotationDelta = rotationDelta,
            zoomFactor = zoomFactor,
            panDelta = scaledPanDelta,
            centroid = centroid
        )
    }

    private fun transform(
        rotationDelta: Float,
        zoomFactor: Float,
        panDelta: Offset,
        centroid: Offset
    ): Offset {
        val previousAngle = angle
        val newAngle = clampAngle(previousAngle + rotationDelta)
        angle = newAngle

        val previousScale = scale
        val newScale = clampScale(previousScale * zoomFactor)
        scale = newScale

        val prePanOffset = clampOffset(
            offset.transform(
                scale = newScale / previousScale,
                angle = newAngle - previousAngle,
                centroid = centroid,
            )
        )

        val postPanOffset = clampOffset(
            prePanOffset.transform(offset = panDelta)
        )

        offset = postPanOffset
        return prePanOffset - postPanOffset
    }

    private val floatPairVectorConverter = TwoWayConverter<Pair<Float, Float>, AnimationVector2D>(
        convertToVector = { AnimationVector2D(it.first, it.second) },
        convertFromVector = { it.v1 to it.v2 }
    )

    private var fling: Job? = null

    fun cancelFling() = fling?.cancel()

    internal suspend fun fling(
        initialRotationVelocity: Float,
        initialLogZoomVelocity: Float,
        initialPanVelocity: Velocity,
        centroid: Offset,
        overscrollEffect: OverscrollEffect? = null
    ) {
        fling?.cancel()
        fling = currentCoroutineContext().job
        coroutineScope {
            launch {
                var previousAngle = angle
                var previousScale = scale
                Animatable(
                    initialValue = previousAngle to ln(previousScale),
                    typeConverter = floatPairVectorConverter
                ).run {
                    val rotationBounds = this@LazyTransformableLayoutState.rotationBounds
                    val clampedZoomBounds = scaleBounds
                    updateBounds(
                        lowerBound = rotationBounds.start to ln(clampedZoomBounds.start),
                        upperBound = rotationBounds.endInclusive to ln(clampedZoomBounds.endInclusive)
                    )

                    animateDecay(
                        initialVelocity = initialRotationVelocity to initialLogZoomVelocity,
                        animationSpec = rotateZoomFlingDecay
                    ) {
                        val newAngle = value.first
                        angle = newAngle

                        val newScale = exp(value.second)
                        scale = newScale

                        offset = clampOffset(
                            offset.transform(
                                scale = newScale / previousScale,
                                angle = newAngle - previousAngle,
                                centroid = centroid
                            )
                        )

                        previousAngle = newAngle
                        previousScale = newScale

                        val rotationBounds = this@LazyTransformableLayoutState.rotationBounds
                        val clampedZoomBounds = this@LazyTransformableLayoutState.scaleBounds
                        updateBounds(
                            lowerBound = rotationBounds.start to ln(clampedZoomBounds.start),
                            upperBound = rotationBounds.endInclusive to ln(clampedZoomBounds.endInclusive)
                        )
                    }
                }
            }

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
                val postClampOffset = clampOffset(preClampOffset)
                offset = postClampOffset

                previousValue = currentValue
            } while (postClampOffset == preClampOffset && !isFinishedFromNanos(playTimeNanos))
        }

        return currentVelocity
    }

    companion object {
        fun saver(
            panFlingDecay: FloatDecayAnimationSpec,
            rotateZoomFlingDecay: DecayAnimationSpec<Pair<Float, Float>>
        ) = listSaver(
            save = { state ->
                state.run {
                    listOf(
                        layoutBounds.left,
                        layoutBounds.top,
                        layoutBounds.right,
                        layoutBounds.bottom,
                        rotationBounds.start,
                        rotationBounds.endInclusive,
                        zoomBounds.start,
                        zoomBounds.endInclusive,
                        offset.x,
                        offset.y,
                        angle,
                        scale
                    )
                }
            },
            restore = { values ->
                LazyTransformableLayoutState(
                    layoutBounds = Rect(
                        left = values[0],
                        top = values[1],
                        right = values[2],
                        bottom = values[3]
                    ),
                    rotationBounds = values[4]..values[5],
                    zoomBounds = values[6]..values[7],
                    initialOffset = Offset(values[8], values[9]),
                    initialAngle = values[10],
                    initialScale = values[11],
                    panFlingDecay = panFlingDecay,
                    rotateZoomFlingDecay = rotateZoomFlingDecay
                )
            }
        )
    }
}
