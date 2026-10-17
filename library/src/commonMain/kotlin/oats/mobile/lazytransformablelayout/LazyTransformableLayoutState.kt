package oats.mobile.lazytransformablelayout

import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
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
import kotlin.math.sin

/**
 * The state of the LazyTransformableLayout
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
@Stable
class LazyTransformableLayoutState(
    layoutBounds: DpRect,
    rotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
    zoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
    initialCenter: DpOffset? = null,
    initialAngle: Float = 0f,
    @FloatRange(from = 0.0, fromInclusive = false) initialScale: Float = 1f,
    private val panFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec(),
    private val rotationFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec(),
    private val zoomFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec()
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

    private var density by mutableStateOf<Density?>(null)

    private val layoutBoundsPx by derivedStateOf {
        density?.run { this@LazyTransformableLayoutState.layoutBounds.toRect() }
    }

    private inline fun <T> measured(block: IntSize.(layoutBounds: Rect) -> T): T? {
        val constraints = constraints ?: return null
        val layoutBounds = layoutBoundsPx ?: return null
        return constraints.block(layoutBounds)
    }

    internal fun acceptConstraints(incomingConstraints: Constraints, density: Density) {
        val newConstraints = incomingConstraints.run { IntSize(maxWidth, maxHeight) }
        if (constraints != newConstraints || this.density != density) {
            val center = this.center
            constraints = newConstraints
            this.density = density
            pendingCenter = null
            applyCamera(angle, scale, center)
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
        measured { layoutBounds ->
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
        measured { layoutBounds ->
            val startIndex = (-floor(angle / 90f).toInt() - 1).mod(4)
            fun vertex(index: Int) = layoutBounds
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
        layoutBounds: DpRect? = null,
        rotationBounds: ClosedFloatingPointRange<Float>? = null,
        zoomBounds: ClosedFloatingPointRange<Float>? = null
    ) {
        if (layoutBounds != null || rotationBounds != null || zoomBounds != null) {
            cancelFling()
            val center = this.center

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

            applyCamera(angle, scale, center)
        }
    }

    internal var offset by mutableStateOf(Offset.Zero)
        private set

    private fun clampOffset(offset: Offset) = panningBounds?.clamp(offset) ?: offset

    private var pendingCenter by mutableStateOf<DpOffset?>(
        initialCenter ?: layoutBounds.run { DpOffset((left + right) / 2, (top + bottom) / 2) }
    )

    /**
     * The point of the layout, in the same space as the items' bounds, at the center of the viewport
     */
    val center: DpOffset
        get() = pendingCenter ?: requireNotNull(density).run {
            val viewportCenter = requireNotNull(constraints).toSize().center
            (viewportCenter + offset)
                .transform(scale = 1 / scale, angle = -angle)
                .let { DpOffset(it.x.toDp(), it.y.toDp()) }
        }

    operator fun component1() = center

    var angle by mutableFloatStateOf(clampAngle(initialAngle))
        private set

    private fun clampAngle(angle: Float) = angle.coerceIn(this.rotationBounds)

    operator fun component2() = angle

    var scale by mutableFloatStateOf(clampScale(initialScale))
        private set

    private fun clampScale(scale: Float) = scale.coerceIn(scaleBounds)

    operator fun component3() = scale

    /**
     * Snaps the camera to the given values, clamped to their bounds. A null value keeps its current value,
     * so changing only the angle or scale rotates or zooms about the center of the viewport.
     *
     * @param angle rotation angle in degrees
     * @param scale zoom scale
     * @param center point of the layout, in the same space as the items' bounds, to place at the center of the viewport
     * @return for angle, scale and center, whether the value was within bounds, or null if it was not updated
     */
    fun set(angle: Float? = null, scale: Float? = null, center: DpOffset? = null): Array<Boolean?> {
        if (angle == null && scale == null && center == null) return arrayOfNulls(3)

        cancelFling()
        return applyCamera(angle ?: this.angle, scale ?: this.scale, center ?: this.center).apply {
            if (angle == null) this[0] = null
            if (angle == null && scale == null) this[1] = null
        }
    }

    private fun applyCamera(angle: Float, scale: Float, center: DpOffset): Array<Boolean?> {
        val clampedAngle = clampAngle(angle)
        this.angle = clampedAngle

        val clampedScale = clampScale(scale)
        this.scale = clampedScale

        val constraints = constraints
        val density = density
        val centerInBounds = if (constraints == null || density == null) {
            pendingCenter = center
            null
        } else {
            val preClampOffset = with(density) { Offset(center.x.toPx(), center.y.toPx()) }
                .transform(clampedScale, clampedAngle) - constraints.toSize().center
            val clampedOffset = clampOffset(preClampOffset)
            offset = clampedOffset
            clampedOffset == preClampOffset
        }

        return arrayOf(clampedAngle == angle, clampedScale == scale, centerInBounds)
    }

    fun transform(
        centroid: Offset,
        rotationDelta: Float = 0f,
        zoomFactor: Float = 1f,
        panDelta: Offset = Offset.Zero,
        overscrollEffect: OverscrollEffect? = null,
        nestedScrollSource: NestedScrollSource = NestedScrollSource.UserInput
    ) {
        cancelFling()

        val scaledPanDelta = panDelta / zoomFactor
        overscrollEffect?.applyToScroll(scaledPanDelta, nestedScrollSource) { scrollDelta ->
            transform(
                centroid = centroid,
                rotationDelta = rotationDelta,
                zoomFactor = zoomFactor,
                panDelta = scrollDelta
            )
        } ?: transform(
            centroid = centroid,
            rotationDelta = rotationDelta,
            zoomFactor = zoomFactor,
            panDelta = scaledPanDelta
        )
    }

    private fun transform(
        centroid: Offset,
        rotationDelta: Float = 0f,
        zoomFactor: Float = 1f,
        panDelta: Offset = Offset.Zero
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
            val rotation = Animatable(angle)
            if (initialRotationVelocity != 0f) launch {
                rotation.run {
                    rotationBounds.run {
                        updateBounds(start, endInclusive)
                    }

                    animateDecay(
                        initialVelocity = initialRotationVelocity,
                        animationSpec = rotationFlingDecay.generateDecayAnimationSpec()
                    ) {
                        transform(
                            centroid = centroid,
                            rotationDelta = value - angle
                        )
                    }
                }
            }

            var zoomRunning = true
            launch {
                try {
                    animateFling(zoomFlingDecay, initialLogZoomVelocity) { logDelta ->
                        val zoomFactor = exp(logDelta)
                        val targetScale = scale * zoomFactor
                        transform(
                            centroid = centroid,
                            zoomFactor = zoomFactor
                        )
                        scale == targetScale || rotation.isRunning
                    }
                } finally {
                    zoomRunning = false
                }
            }

            val boundsChanging = { rotation.isRunning || zoomRunning }

            launch {
                overscrollEffect?.applyToFling(initialPanVelocity.copy(y = 0f)) { velocity ->
                    flingX(velocity.x, boundsChanging)
                } ?: flingX(initialPanVelocity.x, boundsChanging)
            }

            launch {
                overscrollEffect?.applyToFling(initialPanVelocity.copy(x = 0f)) { velocity ->
                    flingY(velocity.y, boundsChanging)
                } ?: flingY(initialPanVelocity.y, boundsChanging)
            }
        }
    }

    private suspend fun flingX(xVelocity: Float, boundsChanging: () -> Boolean) = Velocity(
        x = flingPan(xVelocity, boundsChanging) { previousOffset, delta ->
            val newOffset = clampOffset(previousOffset.copy(x = previousOffset.x + delta))
            newOffset to newOffset.x - previousOffset.x
        },
        y = 0f
    )

    private suspend fun flingY(yVelocity: Float, boundsChanging: () -> Boolean) = Velocity(
        x = 0f,
        y = flingPan(yVelocity, boundsChanging) { previousOffset, delta ->
            val newOffset = clampOffset(previousOffset.copy(y = previousOffset.y + delta))
            newOffset to newOffset.y - previousOffset.y
        }
    )

    private suspend fun flingPan(
        initialVelocity: Float,
        boundsChanging: () -> Boolean,
        applyDelta: (previousOffset: Offset, delta: Float) -> Pair<Offset, Float>
    ) = animateFling(panFlingDecay, initialVelocity) { delta ->
        val (newOffset, consumed) = applyDelta(offset, delta)
        offset = newOffset
        delta == 0f || consumed / delta > MIN_PAN_FLING_PROGRESS || boundsChanging()
    }

    private suspend fun animateFling(
        animationSpec: FloatDecayAnimationSpec,
        initialVelocity: Float,
        applyDelta: (Float) -> Boolean
    ) = if (initialVelocity == 0f) {
        0f
    } else AnimationState(0f, initialVelocity).run {
        var previousValue = 0f
        animateDecay(animationSpec.generateDecayAnimationSpec()) {
            if (!applyDelta(value - previousValue)) cancelAnimation()
            previousValue = value
        }
        velocity
    }

    companion object {
        private const val MIN_PAN_FLING_PROGRESS = 0.1f

        fun saver(
            panFlingDecay: FloatDecayAnimationSpec,
            rotationFlingDecay: FloatDecayAnimationSpec,
            zoomFlingDecay: FloatDecayAnimationSpec
        ) = listSaver<LazyTransformableLayoutState, Float>(
            save = { state ->
                state.run {
                    listOf(
                        layoutBounds.left.value,
                        layoutBounds.top.value,
                        layoutBounds.right.value,
                        layoutBounds.bottom.value,
                        rotationBounds.start,
                        rotationBounds.endInclusive,
                        zoomBounds.start,
                        zoomBounds.endInclusive,
                        center.x.value,
                        center.y.value,
                        angle,
                        scale
                    )
                }
            },
            restore = { values ->
                LazyTransformableLayoutState(
                    layoutBounds = DpRect(
                        left = values[0].dp,
                        top = values[1].dp,
                        right = values[2].dp,
                        bottom = values[3].dp
                    ),
                    rotationBounds = values[4]..values[5],
                    zoomBounds = values[6]..values[7],
                    initialCenter = DpOffset(values[8].dp, values[9].dp),
                    initialAngle = values[10],
                    initialScale = values[11],
                    panFlingDecay = panFlingDecay,
                    rotationFlingDecay = rotationFlingDecay,
                    zoomFlingDecay = zoomFlingDecay
                )
            }
        )
    }
}
