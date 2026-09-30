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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable
fun rememberLazyTransformableLayoutState(
    layoutBounds: Rect,
    rotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
    zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
    initialOffset: Offset = Offset.Zero,
    initialAngle: Float = 0f,
    initialScale: Float = 1f,
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
            initialLayoutBounds = layoutBounds,
            initialRotationBounds = rotationBounds,
            initialZoomBounds = zoomBounds,
            initialOffset = initialOffset,
            initialAngle = initialAngle,
            initialScale = initialScale,
            panFlingDecay = panFlingDecay,
            rotateZoomFlingDecay = rotateZoomFlingDecay
        )
    }
}

/**
 * The state of the LazyTransformableLayout
 *
 * @param initialLayoutBounds The bounds of the layout which can be panned into view
 * @param initialRotationBounds min and max angle bounds in degrees
 * @param initialZoomBounds min and max scale bounds
 * @param initialOffset initial offset of the top left corner of the viewport relative to the layoutBounds
 * @param initialAngle initial rotation angle in degrees
 * @param initialScale initial zoom scale (greater than zero)
 * @param panFlingDecay decay animation spec for panning fling velocity
 * @param rotateZoomFlingDecay decay animation spec for zoom and rotation fling velocity
 */
@Stable
class LazyTransformableLayoutState(
    initialLayoutBounds: Rect,
    initialRotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
    initialZoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
    initialOffset: Offset = Offset.Zero,
    initialAngle: Float = 0f,
    @FloatRange(from = 0.0, fromInclusive = false) initialScale: Float = 1f,
    private val panFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec(),
    private val rotateZoomFlingDecay: DecayAnimationSpec<Pair<Float, Float>> = exponentialDecay()
) {
    var layoutBounds by mutableStateOf(initialLayoutBounds)
        internal set

    var rotationBounds by mutableStateOf(initialRotationBounds)
        internal set

    var zoomBounds by mutableStateOf(initialZoomBounds)
        internal set

    init {
        require(zoomBounds.start > 0) { "zoomBounds must be positive." }

        initialRotationBounds.run {
            require(!isEmpty()) {
                "max rotation bound ($endInclusive) must be greater than or equal to min rotation bound ($start)."
            }
        }

        initialZoomBounds.run {
            require(!isEmpty()) {
                "max zoom bound ($endInclusive) must be greater than or equal to min zoom bound ($start)."
            }
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

    private fun clampOffset(offset: Offset) = offset.clamp(panningBounds)

    operator fun component1() = offset

    var angle by mutableFloatStateOf(clampAngle(initialAngle))
        private set

    private fun clampAngle(angle: Float) = angle.coerceIn(rotationBounds)

    operator fun component2() = angle

    var scale by mutableFloatStateOf(clampScale(initialScale))
        private set

    private fun clampScale(scale: Float) = scale.coerceIn(minScaleBound, zoomBounds.endInclusive)

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

    private var constraints by mutableStateOf<IntSize?>(null)

    internal fun acceptConstraints(incomingConstraints: Constraints) {
        val previousConstraints = constraints
        constraints = incomingConstraints.run { IntSize(maxWidth, maxHeight) }
        if (previousConstraints == null) {
            offset = clampOffset(offset)
            scale = scale.coerceAtLeast(minScaleBound)
        }
    }

    private val minScaleBound by derivedStateOf {
        val lowerZoomBound = zoomBounds.start
        constraints?.run {
            val layoutBounds = layoutBounds
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

    private val layoutBoundVertices by derivedStateOf { layoutBounds.vertices }

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

    internal suspend fun fling(
        initialRotationVelocity: Float,
        initialLogZoomVelocity: Float,
        initialPanVelocity: Velocity,
        centroid: Offset,
        overscrollEffect: OverscrollEffect? = null
    ) = coroutineScope {
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

                    updateBounds(rotationBounds.start to ln(minScaleBound))
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
        ): Saver<LazyTransformableLayoutState, *> = listSaver(
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
                    initialLayoutBounds = Rect(
                        left = values[0],
                        top = values[1],
                        right = values[2],
                        bottom = values[3]
                    ),
                    initialRotationBounds = values[4]..values[5],
                    initialZoomBounds = values[6]..values[7],
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
