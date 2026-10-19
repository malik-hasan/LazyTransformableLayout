package oats.mobile.lazytransformablelayout

import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.width
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.extension.center
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
 * @param layoutBounds The bounds of the layout which can be panned into view, in the same space as the items' bounds
 * @param rotationBounds min and max angle bounds in degrees
 * @param zoomBounds min and max scale bounds
 * @param initialCenter initial point of the layout at the center of the viewport's content area, defaulting to the
 * center of [layoutBounds]
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
    initialCenter: DpOffset = layoutBounds.center,
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

    private var contentBounds by mutableStateOf<Rect?>(null)

    internal val contentCenter get() = contentBounds?.center

    private var density by mutableStateOf<Density?>(null)

    private val layoutBoundsPx by derivedStateOf {
        density?.run { this@LazyTransformableLayoutState.layoutBounds.toRect() }
    }

    private inline fun <T> measured(block: (contentBounds: Rect, density: Density, layoutBounds: Rect) -> T) =
        contentBounds?.let { contentBounds ->
            density?.let { density ->
                layoutBoundsPx?.let { layoutBoundsPx ->
                    block(contentBounds, density, layoutBoundsPx)
                }
            }
        }


    internal fun measure(constraints: Constraints, density: Density, contentBounds: Rect) {
        val constraintsSize = constraints.run { IntSize(maxWidth, maxHeight) }
        if (this.constraints != constraintsSize || this.density != density || this.contentBounds != contentBounds) {
            this.constraints = constraintsSize
            this.density = density
            this.contentBounds = contentBounds
            val scale = pendingFit?.let { (bounds, padding) -> fitScale(bounds, angle, padding) } ?: scale
            pendingFit = null
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

    private val scaleBounds by derivedStateOf { scaleBoundsAt(angle) }

    private fun scaleBoundsAt(angle: Float) = measured { contentBounds, _, layoutBounds ->
        val angleRadians = angle.radians
        val cos = abs(cos(angleRadians))
        val sin = abs(sin(angleRadians))
        val start = maxOf(
            zoomBounds.start,
            contentBounds.width / layoutBounds.run { width * cos + height * sin },
            contentBounds.height / layoutBounds.run { width * sin + height * cos }
        )
        start..maxOf(start, zoomBounds.endInclusive)
    } ?: zoomBounds

    private val panningBounds by derivedStateOf { panningBoundsAt(angle, scale) }

    private fun panningBoundsAt(angle: Float, scale: Float) = measured { contentBounds, _, layoutBounds ->
        val width = contentBounds.width
        val height = contentBounds.height
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

        val contentCenter = Offset(width / 2, height / 2)
        Parallelogram(
            listOf(
                Offset(left, (leftmost.y - height / 2).coerceIn(top, bottom)),
                Offset((topmost.x - width / 2).coerceIn(left, right), top),
                Offset(right, (rightmost.y - height / 2).coerceIn(top, bottom)),
                Offset((bottommost.x - width / 2).coerceIn(left, right), bottom)
            ).map { (it + contentCenter).transform(scale = 1 / scale, angle = -angle) }
        )
    }

    fun updateBounds(
        layoutBounds: DpRect? = null,
        rotationBounds: ClosedFloatingPointRange<Float>? = null,
        zoomBounds: ClosedFloatingPointRange<Float>? = null
    ) {
        if (layoutBounds != null || rotationBounds != null || zoomBounds != null) {
            cancelFling()

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

    /**
     * The point of the layout, in the same space as the items' bounds, at the center of the viewport's content area
     */
    var center by mutableStateOf(initialCenter)
        private set

    private fun clampCenter(center: Offset) = panningBounds?.clamp(center) ?: center

    internal val offset by derivedStateOf {
        measured { contentBounds, density, _ ->
            density.toPx(center).transform(scale, angle) - contentBounds.center
        } ?: Offset.Zero
    }

    private fun Density.toPx(offset: DpOffset) = Offset(offset.x.toPx(), offset.y.toPx())

    private fun Density.toDp(offset: Offset) = DpOffset(offset.x.toDp(), offset.y.toDp())

    var angle by mutableFloatStateOf(clampAngle(initialAngle))
        private set

    private fun clampAngle(angle: Float) = angle.coerceIn(this.rotationBounds)

    var scale by mutableFloatStateOf(clampScale(initialScale))
        private set

    private fun clampScale(scale: Float) = scale.coerceIn(scaleBounds)

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
        pendingFit = null
        animationRequest = null
        return applyCamera(angle ?: this.angle, scale ?: this.scale, center ?: this.center).apply {
            if (angle == null) this[0] = null
            if (angle == null && scale == null) this[1] = null
        }
    }

    /**
     * Animates the camera to the given values, clamped to their bounds. Each frame interpolates the angle linearly,
     * the scale in log space, and the center linearly in layout space, then applies them like [set].
     * A null value keeps its current value, so changing only the angle or scale rotates or zooms about the
     * center of the viewport. Cancels any ongoing fling or animation, and is cancelled by touch input.
     *
     * @param angle rotation angle in degrees
     * @param scale zoom scale
     * @param center point of the layout, in the same space as the items' bounds, to place at the center of the viewport
     * @param animationSpec the spec animating progress from 0 to 1
     * @return for angle, scale and center, whether the value was within bounds, or null if it was not updated
     */
    suspend fun animate(
        angle: Float? = null,
        scale: Float? = null,
        center: DpOffset? = null,
        animationSpec: AnimationSpec<Float> = spring()
    ): Array<Boolean?> {
        if (angle == null && scale == null && center == null) return arrayOfNulls(3)

        val density = density
        if (contentBounds == null || density == null) return set(angle, scale, center)

        fling?.cancel()
        fling = currentCoroutineContext().job

        val startAngle = this.angle
        val startScale = this.scale
        val startCenter = this.center

        val targetAngle = clampAngle(angle ?: startAngle)
        val targetScale = (scale ?: startScale).coerceIn(scaleBoundsAt(targetAngle))
        val targetCenter = (center ?: startCenter).let {
            val centerPx = density.toPx(it)
            val clampedCenter = panningBoundsAt(targetAngle, targetScale)?.clamp(centerPx) ?: centerPx
            if (clampedCenter == centerPx) it else density.toDp(clampedCenter)
        }
        val logZoom = ln(targetScale / startScale)

        AnimationState(0f).animateTo(1f, animationSpec) {
            applyCamera(
                angle = lerp(startAngle, targetAngle, value),
                scale = startScale * exp(logZoom * value),
                center = lerp(startCenter, targetCenter, value)
            )
        }

        return applyCamera(angle ?: startAngle, scale ?: startScale, center ?: startCenter).apply {
            if (angle == null) this[0] = null
            if (angle == null && scale == null) this[1] = null
        }
    }

    /**
     * Snaps the camera to center [bounds] in the viewport, at the largest scale that fits them at the target angle.
     * The values are clamped to their bounds like the other [set].
     *
     * @param bounds area of the layout, in the same space as the items' bounds, to fit in the viewport
     * @param angle rotation angle in degrees, or null to keep the current angle
     * @param padding space to keep between [bounds] and each edge of the viewport
     * @return for angle, scale and center, whether the value was within bounds, or null if it was not updated
     */
    fun set(bounds: DpRect, angle: Float? = null, padding: Dp = 0.dp): Array<Boolean?> {
        val scale = fitScale(bounds, clampAngle(angle ?: this.angle), padding)
        return set(angle, scale ?: this.scale, bounds.center).also {
            if (scale == null) pendingFit = bounds to padding
        }
    }

    /**
     * Animates the camera to center [bounds] in the viewport, at the largest scale that fits them at the target angle.
     * Interpolates and clamps like the other [animate]. Suspends until the layout is first measured,
     * since the fitting scale depends on the viewport size.
     *
     * @param bounds area of the layout, in the same space as the items' bounds, to fit in the viewport
     * @param angle rotation angle in degrees, or null to keep the current angle
     * @param padding space to keep between [bounds] and each edge of the viewport
     * @param animationSpec the spec animating progress from 0 to 1
     * @return for angle, scale and center, whether the value was within bounds, or null if it was not updated
     */
    suspend fun animate(
        bounds: DpRect,
        angle: Float? = null,
        padding: Dp = 0.dp,
        animationSpec: AnimationSpec<Float> = spring()
    ): Array<Boolean?> {
        snapshotFlow { contentBounds != null && density != null }.first { it }
        return animate(angle, fitScale(bounds, clampAngle(angle ?: this.angle), padding), bounds.center, animationSpec)
    }

    private var animationRequest by mutableStateOf<(suspend () -> Unit)?>(null)

    /**
     * Requests the same animation as [animate] without suspending, so it can be called outside a composition-bound
     * coroutine, such as from a ViewModel. The animation runs on the next frame while the layout is composed,
     * replacing any earlier request. A [set] before it starts discards the request.
     *
     * @param angle rotation angle in degrees
     * @param scale zoom scale
     * @param center point of the layout, in the same space as the items' bounds, to place at the center of the viewport
     * @param animationSpec the spec animating progress from 0 to 1
     */
    fun requestAnimate(
        angle: Float? = null,
        scale: Float? = null,
        center: DpOffset? = null,
        animationSpec: AnimationSpec<Float> = spring()
    ) {
        animationRequest = { animate(angle, scale, center, animationSpec) }
    }

    /**
     * Requests the same animation as the [animate] that fits [bounds], without suspending. Runs like the other
     * [requestAnimate].
     *
     * @param bounds area of the layout, in the same space as the items' bounds, to fit in the viewport
     * @param angle rotation angle in degrees, or null to keep the current angle
     * @param padding space to keep between [bounds] and each edge of the viewport
     * @param animationSpec the spec animating progress from 0 to 1
     */
    fun requestAnimate(
        bounds: DpRect,
        angle: Float? = null,
        padding: Dp = 0.dp,
        animationSpec: AnimationSpec<Float> = spring()
    ) {
        animationRequest = { animate(bounds, angle, padding, animationSpec) }
    }

    internal suspend fun runAnimationRequests() = snapshotFlow { animationRequest }.filterNotNull().collectLatest {
        animationRequest = null
        it()
    }

    private var pendingFit: Pair<DpRect, Dp>? = null

    private fun fitScale(bounds: DpRect, angle: Float, padding: Dp): Float? {
        val contentBounds = contentBounds ?: return null
        val density = density ?: return null
        return with(density) {
            val angleRadians = angle.radians
            val cos = abs(cos(angleRadians))
            val sin = abs(sin(angleRadians))
            val width = bounds.width.toPx()
            val height = bounds.height.toPx()
            val totalPadding = 2 * padding.toPx()
            minOf(
                (contentBounds.width - totalPadding) / (width * cos + height * sin),
                (contentBounds.height - totalPadding) / (width * sin + height * cos)
            )
        }
    }

    private fun applyCamera(angle: Float, scale: Float, center: DpOffset): Array<Boolean?> {
        val clampedAngle = clampAngle(angle)
        this.angle = clampedAngle

        val clampedScale = clampScale(scale)
        this.scale = clampedScale

        val density = density
        val centerInBounds = if (contentBounds == null || density == null) {
            this.center = center
            null
        } else {
            val centerPx = density.toPx(center)
            val clampedCenter = clampCenter(centerPx)
            this.center = if (clampedCenter == centerPx) center else density.toDp(clampedCenter)
            clampedCenter == centerPx
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
        val density = density ?: return Offset.Zero
        val contentCenter = contentBounds?.center ?: return Offset.Zero

        val previousAngle = angle
        val newAngle = clampAngle(previousAngle + rotationDelta)
        angle = newAngle

        val previousScale = scale
        val newScale = clampScale(previousScale * zoomFactor)
        scale = newScale

        val centroidFromCenter = centroid - contentCenter
        center = density.toDp(
            clampCenter(
                density.toPx(center)
                    + centroidFromCenter.transform(scale = 1 / previousScale, angle = -previousAngle)
                    - centroidFromCenter.transform(scale = 1 / newScale, angle = -newAngle)
            )
        )

        return -panBy(-panDelta)
    }

    private fun panBy(screenDelta: Offset): Offset {
        val density = density ?: return Offset.Zero
        val previousCenter = density.toPx(center)
        val newCenter = clampCenter(previousCenter + screenDelta.transform(scale = 1 / scale, angle = -angle))
        center = density.toDp(newCenter)
        return (newCenter - previousCenter).transform(scale, angle)
    }

    internal suspend fun animateZoom(centroid: Offset, zoomFactor: Float) {
        fling?.cancel()
        fling = currentCoroutineContext().job

        var previousLogZoom = 0f
        AnimationState(0f).animateTo(ln(clampScale(scale * zoomFactor) / scale), spring()) {
            transform(centroid = centroid, zoomFactor = exp(value - previousLogZoom))
            previousLogZoom = value
        }
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
        if (initialRotationVelocity != 0f || initialLogZoomVelocity != 0f || initialPanVelocity != Velocity.Zero) {
            fling?.cancel()
            fling = currentCoroutineContext().job
        }

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
        x = flingPan(xVelocity, boundsChanging) { delta -> panBy(Offset(delta, 0f)).x },
        y = 0f
    )

    private suspend fun flingY(yVelocity: Float, boundsChanging: () -> Boolean) = Velocity(
        x = 0f,
        y = flingPan(yVelocity, boundsChanging) { delta -> panBy(Offset(0f, delta)).y }
    )

    private suspend fun flingPan(
        initialVelocity: Float,
        boundsChanging: () -> Boolean,
        pan: (delta: Float) -> Float
    ) = animateFling(panFlingDecay, initialVelocity) { delta ->
        delta == 0f || pan(delta) / delta > MIN_PAN_FLING_PROGRESS || boundsChanging()
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
