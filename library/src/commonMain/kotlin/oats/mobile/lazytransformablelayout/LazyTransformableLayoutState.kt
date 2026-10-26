package oats.mobile.lazytransformablelayout

import androidx.annotation.FloatRange
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasureScope
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.toSize
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
import oats.mobile.lazytransformablelayout.extension.toDp
import oats.mobile.lazytransformablelayout.extension.toPx
import oats.mobile.lazytransformablelayout.extension.transform
import oats.mobile.lazytransformablelayout.extension.vertices
import oats.mobile.lazytransformablelayout.model.Parallelogram
import oats.mobile.lazytransformablelayout.model.Viewport
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin

/**
 * The state of the LazyTransformableLayout
 *
 * @param initialLayoutBounds The bounds of the layout which can be panned into view, in the same space as the items' bounds
 * @param initialRotationBounds min and max angle bounds in degrees
 * @param initialZoomBounds min and max scale bounds
 * @param initialCenterOffset initial point of the layout at the center of the viewport's content area, defaulting to the
 * center of [initialLayoutBounds]
 * @param initialRotationAngle initial rotation angle in degrees
 * @param initialZoomScale initial zoom scale (greater than zero)
 * @param panFlingDecay decay animation spec for panning fling velocity
 * @param rotationFlingDecay decay animation spec for rotation fling velocity in degrees
 * @param zoomFlingDecay decay animation spec for zoom fling velocity in log scale
 */
@Stable
class LazyTransformableLayoutState(
    initialLayoutBounds: DpRect,
    initialRotationBounds: ClosedFloatingPointRange<Float> = Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY,
    initialZoomBounds: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
    initialCenterOffset: DpOffset = initialLayoutBounds.center,
    initialRotationAngle: Float = 0f,
    @FloatRange(from = 0.0, fromInclusive = false) initialZoomScale: Float = 1f,
    private val panFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec(),
    private val rotationFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec(),
    private val zoomFlingDecay: FloatDecayAnimationSpec = FloatExponentialDecaySpec()
) {
    internal var layoutBounds by mutableStateOf(initialLayoutBounds)
        private set

    internal var rotationBounds by mutableStateOf(initialRotationBounds)
        private set

    internal var zoomBounds by mutableStateOf(initialZoomBounds)
        private set

    init {
        initialRotationBounds.run {
            require(!isEmpty()) {
                "max rotation bound ($endInclusive) must be greater than or equal to min rotation bound ($start)."
            }
        }

        initialZoomBounds.run {
            require(!isEmpty()) {
                "max zoom bound ($endInclusive) must be greater than or equal to min zoom bound ($start)."
            }

            require(start > 0) { "zoomBounds must be positive." }
        }
    }

    private var viewport by mutableStateOf<Viewport?>(null)

    private val measured
        get() = checkNotNull(viewport) {
            "LazyTransformableLayoutState used before its layout was measured"
        }

    private val contentCenter
        get() = measured.contentBounds.center

    private val layoutBoundsPx by derivedStateOf {
        with(measured) { layoutBounds.toRect() }
    }

    context(measureScope: LazyLayoutMeasureScope)
    internal fun measure(constraints: Constraints, contentPadding: PaddingValues) {
        val constraints = constraints.run { IntSize(maxWidth, maxHeight) }

        val contentBounds = contentPadding.run {
            with(measureScope) {
                val contentLeft = calculateLeftPadding(layoutDirection).toPx()
                val contentTop = calculateTopPadding().toPx()
                Rect(
                    left = contentLeft,
                    top = contentTop,
                    right = maxOf(
                        contentLeft,
                        constraints.width - calculateRightPadding(layoutDirection).toPx()
                    ),
                    bottom = maxOf(
                        contentTop,
                        constraints.height - calculateBottomPadding().toPx()
                    )
                )
            }
        }

        val viewport = Viewport(measureScope, constraints, contentBounds)
        if (this.viewport != viewport) {
            this.viewport = viewport

            val zoomScale = pendingFitBounds?.let { (bounds, margin) ->
                boundsFitScale(bounds, rotationAngle, margin)
            } ?: zoomScale
            pendingFitBounds = null

            clampAndSet(
                rotationAngle = rotationAngle,
                zoomScale = zoomScale,
                centerOffset = centerOffset
            )
        }
    }

    private var previousCompositionBounds: Pair<Float, Parallelogram>? = null

    internal val compositionBounds by derivedStateOf(referentialEqualityPolicy()) {
        val minItemDimension = 0.5f / zoomScale
        previousCompositionBounds?.takeIf { (previousMinItemDimension, previousBounds) ->
            minItemDimension * 1.19f >= previousMinItemDimension
                && viewportBounds(128f) in previousBounds
        } ?: (minItemDimension to viewportBounds(256f))
            .also { previousCompositionBounds = it }
    }

    private fun viewportBounds(buffer: Float) = measured.size.toSize().toRect()
        .inflate(buffer)
        .run {
            Parallelogram(
                vertices.map {
                    (it + offset).transform(scale = 1 / zoomScale, angle = -rotationAngle)
                }
            )
        }

    private val scaleBounds by derivedStateOf {
        viewport?.let {
            scaleBoundsAt(rotationAngle)
        } ?: zoomBounds
    }

    private fun scaleBoundsAt(rotationAngle: Float): ClosedFloatingPointRange<Float> {
        val contentBounds = measured.contentBounds
        val layoutBounds = layoutBoundsPx
        val angleRadians = rotationAngle.radians
        val cos = abs(cos(angleRadians))
        val sin = abs(sin(angleRadians))
        val start = maxOf(
            zoomBounds.start,
            contentBounds.width / layoutBounds.run { width * cos + height * sin },
            contentBounds.height / layoutBounds.run { width * sin + height * cos }
        )
        return start..maxOf(start, zoomBounds.endInclusive)
    }

    private val panningBounds by derivedStateOf { panningBoundsAt(rotationAngle, zoomScale) }

    private fun panningBoundsAt(rotationAngle: Float, zoomScale: Float): Parallelogram {
        val layoutBounds = layoutBoundsPx
        val (width, height) = measured.contentBounds.size
        val startIndex = (-floor(rotationAngle / 90f).toInt() - 1).mod(4)
        fun vertex(index: Int) = layoutBounds
            .vertices[(startIndex + index) % 4]
            .transform(zoomScale, rotationAngle)

        val leftmost = vertex(0)
        val topmost = vertex(1)
        val rightmost = vertex(2)
        val bottommost = vertex(3)

        val left = leftmost.x
        val top = topmost.y
        val right = (rightmost.x - width).coerceAtLeast(left)
        val bottom = (bottommost.y - height).coerceAtLeast(top)

        val contentCenter = Offset(width / 2, height / 2)
        return Parallelogram(
            listOf(
                Offset(left, (leftmost.y - height / 2).coerceIn(top, bottom)),
                Offset((topmost.x - width / 2).coerceIn(left, right), top),
                Offset(right, (rightmost.y - height / 2).coerceIn(top, bottom)),
                Offset((bottommost.x - width / 2).coerceIn(left, right), bottom)
            ).map { (it + contentCenter).transform(scale = 1 / zoomScale, angle = -rotationAngle) }
        )
    }

    /**
     * Replaces any of the bounds, then clamps the camera to them. A null value keeps its current bounds.
     * Cancels any ongoing fling or animation.
     *
     * @param layoutBounds the bounds of the layout which can be panned into view, in the same space as the items' bounds
     * @param rotationBounds min and max rotation angle bounds in degrees
     * @param zoomBounds min and max zoom scale bounds
     */
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
            }

            zoomBounds?.let {
                this.zoomBounds = it
            }

            clampAndSet(rotationAngle, zoomScale, centerOffset)
        }
    }

    /**
     * The point of the layout, in the same space as the items' bounds, at the center of the viewport's content area
     */
    var centerOffset by mutableStateOf(initialCenterOffset)
        private set

    private var centerOffsetPx: Offset
        get() = with(measured) {
            centerOffset.toPx()
        }
        set(value) {
            centerOffset = with(measured) {
                value.toDp()
            }
        }

    private fun clampCenterOffset(center: Offset) = viewport?.let {
        panningBounds.clamp(center)
    } ?: center

    internal val offset by derivedStateOf {
        centerOffsetPx.transform(zoomScale, rotationAngle) - contentCenter
    }

    /**
     * The rotation angle of the camera in degrees, clockwise
     */
    var rotationAngle by mutableFloatStateOf(clampRotationAngle(initialRotationAngle))
        private set

    private fun clampRotationAngle(rotationAngle: Float) = rotationAngle.coerceIn(rotationBounds)

    /**
     * The zoom scale of the camera, where 1 shows the layout at its size in dp
     */
    var zoomScale by mutableFloatStateOf(clampZoomScale(initialZoomScale))
        private set

    private fun clampZoomScale(zoomScale: Float) = zoomScale.coerceIn(scaleBounds)

    private fun clampAndSet(
        rotationAngle: Float? = null,
        zoomScale: Float? = null,
        centerOffset: DpOffset? = null
    ): Array<Boolean?> {
        val clamped = arrayOfNulls<Boolean>(3)

        rotationAngle?.let {
            val clampedAngle = clampRotationAngle(it)
            this.rotationAngle = clampedAngle
            clamped[0] = clampedAngle == it
        }

        if (rotationAngle != null || zoomScale != null) {
            val targetScale = zoomScale ?: this.zoomScale
            val clampedScale = clampZoomScale(targetScale)
            this.zoomScale = clampedScale
            clamped[1] = clampedScale == targetScale
        }

        if (rotationAngle != null || zoomScale != null || centerOffset != null) {
            viewport?.run {
                val targetCenter = centerOffset ?: this@LazyTransformableLayoutState.centerOffset
                val targetCenterPx = targetCenter.toPx()
                val clampedCenterPx = clampCenterOffset(targetCenterPx)
                val inBounds = clampedCenterPx == targetCenterPx
                this@LazyTransformableLayoutState.centerOffset = if (inBounds) targetCenter else clampedCenterPx.toDp()
                clamped[2] = inBounds
            } ?: centerOffset?.let {
                this.centerOffset = it
            }
        }

        return clamped
    }

    /**
     * Rotates and zooms the camera about [centroid], then pans it, like a transform gesture. Each value is clamped
     * to its bounds. Cancels any ongoing fling or animation. Has no effect before the layout is first measured,
     * since the deltas are relative to the screen; use [snapTo] or [animateTo] to set an initial camera.
     *
     * @param centroid point in the layout's own coordinates, in pixels, that stays fixed while rotating and zooming
     * @param rotationDelta change in rotation angle in degrees
     * @param zoomFactor factor to multiply the zoom scale by
     * @param panDelta distance in pixels to move the content on screen
     * @param overscrollEffect the effect to apply any pan that goes past the panning bounds to
     * @param nestedScrollSource the source reported to [overscrollEffect]
     */
    fun transform(
        centroid: Offset,
        rotationDelta: Float = 0f,
        zoomFactor: Float = 1f,
        panDelta: Offset = Offset.Zero,
        overscrollEffect: OverscrollEffect? = null,
        nestedScrollSource: NestedScrollSource = NestedScrollSource.UserInput
    ) {
        if (viewport == null) return
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
        val previousAngle = rotationAngle
        val newAngle = clampRotationAngle(previousAngle + rotationDelta)
        rotationAngle = newAngle

        val previousScale = zoomScale
        val newScale = clampZoomScale(previousScale * zoomFactor)
        zoomScale = newScale

        val centroidFromCenter = centroid - contentCenter
        centerOffsetPx = clampCenterOffset(
            centerOffsetPx
                    + centroidFromCenter.transform(scale = 1 / previousScale, angle = -previousAngle)
                    - centroidFromCenter.transform(scale = 1 / newScale, angle = -newAngle)
        )

        return panBy(-panDelta)
    }

    /**
     * Snaps the camera to the given values, clamped to their bounds. A null value keeps its current value,
     * so changing only the rotation angle or zoom scale rotates or zooms about the center of the viewport.
     * Cancels any ongoing fling or animation.
     *
     * @param rotationAngle rotation angle in degrees
     * @param zoomScale zoom scale
     * @param centerOffset point of the layout, in the same space as the items' bounds, to place at the center of the viewport
     * @return for rotation angle, zoom scale and center offset, whether the value was within bounds, or null if it was not updated
     */
    fun snapTo(rotationAngle: Float? = null, zoomScale: Float? = null, centerOffset: DpOffset? = null): Array<Boolean?> {
        if (rotationAngle != null || zoomScale != null || centerOffset != null) {
            cancelFling()
            pendingAnimation = null
            pendingFitBounds = null
        }

        return clampAndSet(rotationAngle, zoomScale, centerOffset)
    }

    /**
     * Animates the camera to the given values, clamped to their bounds. Each frame interpolates the rotation angle
     * linearly, the zoom scale in log space, and the center offset linearly in layout space, then applies them like
     * [snapTo]. A null value keeps its current value, so changing only the rotation angle or zoom scale rotates or
     * zooms about the center of the viewport. Cancels any ongoing fling or animation, and is cancelled by touch input.
     *
     * @param rotationAngle rotation angle in degrees
     * @param zoomScale zoom scale
     * @param centerOffset point of the layout, in the same space as the items' bounds, to place at the center of the viewport
     * @param animationSpec the spec animating progress from 0 to 1
     * @return for rotation angle, zoom scale and center offset, whether the value was within bounds, or null if it was not updated
     */
    suspend fun animateTo(
        rotationAngle: Float? = null,
        zoomScale: Float? = null,
        centerOffset: DpOffset? = null,
        animationSpec: AnimationSpec<Float> = spring()
    ): Array<Boolean?> {
        if (rotationAngle == null && zoomScale == null && centerOffset == null) return arrayOfNulls(3)

        val viewport = viewport ?: return snapTo(rotationAngle, zoomScale, centerOffset)
        claimFling()

        val startAngle = this.rotationAngle
        val startScale = this.zoomScale
        val startCenter = this.centerOffset

        val targetAngle = clampRotationAngle(rotationAngle ?: startAngle)
        val targetScale = (zoomScale ?: startScale).coerceIn(scaleBoundsAt(targetAngle))
        val targetCenter = (centerOffset ?: startCenter).let {
            val centerPx = with(viewport) { it.toPx() }
            val clampedCenter = panningBoundsAt(targetAngle, targetScale).clamp(centerPx)
            if (clampedCenter == centerPx) it else with(viewport) { clampedCenter.toDp() }
        }
        val logZoom = ln(targetScale / startScale)

        AnimationState(0f).animateTo(1f, animationSpec) {
            clampAndSet(
                rotationAngle = lerp(startAngle, targetAngle, value),
                zoomScale = startScale * exp(logZoom * value),
                centerOffset = lerp(startCenter, targetCenter, value)
            )
        }

        return clampAndSet(rotationAngle, zoomScale, centerOffset)
    }

    /**
     * Snaps the camera to center [bounds] in the viewport, at the largest zoom scale that fits them at the target
     * rotation angle. The values are clamped to their bounds like [snapTo].
     *
     * @param bounds area of the layout, in the same space as the items' bounds, to fit in the viewport
     * @param rotationAngle rotation angle in degrees, or null to keep the current rotation angle
     * @param margin space to keep between [bounds] and each edge of the viewport
     * @return for rotation angle, zoom scale and center offset, whether the value was within bounds, or null if it was not updated
     */
    fun snapToBounds(bounds: DpRect, rotationAngle: Float? = null, margin: Dp = 0.dp): Array<Boolean?> {
        if (viewport == null) return snapTo(rotationAngle, centerOffset = bounds.center).also {
            pendingFitBounds = bounds to margin
        }

        val zoomScale = boundsFitScale(bounds, clampRotationAngle(rotationAngle ?: this.rotationAngle), margin)
        return snapTo(rotationAngle, zoomScale, bounds.center)
    }

    /**
     * Animates the camera to center [bounds] in the viewport, at the largest zoom scale that fits them at the target
     * rotation angle. Interpolates and clamps like [animateTo]. Suspends until the layout is first measured,
     * since the fitting zoom scale depends on the viewport size.
     *
     * @param bounds area of the layout, in the same space as the items' bounds, to fit in the viewport
     * @param rotationAngle rotation angle in degrees, or null to keep the current rotation angle
     * @param margin space to keep between [bounds] and each edge of the viewport
     * @param animationSpec the spec animating progress from 0 to 1
     * @return for rotation angle, zoom scale and center offset, whether the value was within bounds, or null if it was not updated
     */
    suspend fun animateToBounds(
        bounds: DpRect,
        rotationAngle: Float? = null,
        margin: Dp = 0.dp,
        animationSpec: AnimationSpec<Float> = spring()
    ): Array<Boolean?> {
        snapshotFlow { viewport }.filterNotNull().first()
        val zoomScale = boundsFitScale(bounds, clampRotationAngle(rotationAngle ?: this.rotationAngle), margin)
        return animateTo(rotationAngle, zoomScale, bounds.center, animationSpec)
    }

    private var pendingAnimation by mutableStateOf<(suspend () -> Unit)?>(null)

    /**
     * Requests the same animation as [animateTo] without suspending, so it can be called outside a composition-bound
     * coroutine, such as from a ViewModel. The animation runs on the next frame while the layout is composed,
     * replacing any earlier request. A [snapTo] or [snapToBounds] before it starts discards the request.
     *
     * @param rotationAngle rotation angle in degrees
     * @param zoomScale zoom scale
     * @param centerOffset point of the layout, in the same space as the items' bounds, to place at the center of the viewport
     * @param animationSpec the spec animating progress from 0 to 1
     */
    fun requestAnimateTo(
        rotationAngle: Float? = null,
        zoomScale: Float? = null,
        centerOffset: DpOffset? = null,
        animationSpec: AnimationSpec<Float> = spring()
    ) {
        pendingAnimation = { animateTo(rotationAngle, zoomScale, centerOffset, animationSpec) }
    }

    /**
     * Requests the same animation as [animateToBounds] without suspending. Runs like [requestAnimateTo].
     *
     * @param bounds area of the layout, in the same space as the items' bounds, to fit in the viewport
     * @param rotationAngle rotation angle in degrees, or null to keep the current rotation angle
     * @param margin space to keep between [bounds] and each edge of the viewport
     * @param animationSpec the spec animating progress from 0 to 1
     */
    fun requestAnimateToBounds(
        bounds: DpRect,
        rotationAngle: Float? = null,
        margin: Dp = 0.dp,
        animationSpec: AnimationSpec<Float> = spring()
    ) {
        pendingAnimation = { animateToBounds(bounds, rotationAngle, margin, animationSpec) }
    }

    internal suspend fun runPendingAnimations() = snapshotFlow { pendingAnimation }.filterNotNull().collectLatest {
        pendingAnimation = null
        it()
    }

    private var pendingFitBounds: Pair<DpRect, Dp>? = null

    private fun boundsFitScale(bounds: DpRect, rotationAngle: Float, margin: Dp) = with(measured) {
        val angleRadians = rotationAngle.radians
        val cos = abs(cos(angleRadians))
        val sin = abs(sin(angleRadians))
        val (width, height) = bounds.toRect().size
        val totalMargin = 2 * margin.toPx()
        minOf(
            (contentBounds.width - totalMargin) / (width * cos + height * sin),
            (contentBounds.height - totalMargin) / (width * sin + height * cos)
        )
    }

    private fun panBy(screenDelta: Offset): Offset {
        val previousCenter = centerOffsetPx
        val newCenter = clampCenterOffset(previousCenter + screenDelta.transform(scale = 1 / zoomScale, angle = -rotationAngle))
        centerOffsetPx = newCenter
        return (newCenter - previousCenter).transform(zoomScale, rotationAngle)
    }

    internal fun zoomBy(zoomFactor: Float) = transform(centroid = contentCenter, zoomFactor = zoomFactor)

    internal suspend fun animateZoom(zoomFactor: Float, centroid: Offset = contentCenter) {
        claimFling()

        var previousLogZoom = 0f
        AnimationState(0f)
            .animateTo(ln(clampZoomScale(zoomScale * zoomFactor) / zoomScale)) {
                transform(centroid = centroid, zoomFactor = exp(value - previousLogZoom))
                previousLogZoom = value
            }
    }

    private var fling: Job? = null

    /**
     * Cancels any ongoing fling or animation of the camera, leaving it where it is
     */
    fun cancelFling() = fling?.cancel()

    private suspend fun claimFling() {
        val job = currentCoroutineContext().job
        if (fling != job) fling?.cancel()
        fling = job
        pendingAnimation = null
    }

    internal suspend fun fling(
        initialRotationVelocity: Float,
        initialLogZoomVelocity: Float,
        initialPanVelocity: Velocity,
        centroid: Offset = contentCenter,
        overscrollEffect: OverscrollEffect? = null
    ) {
        if (initialRotationVelocity != 0f || initialLogZoomVelocity != 0f || initialPanVelocity != Velocity.Zero) {
            claimFling()
        }

        coroutineScope {
            var rotationRunning = true
            launch {
                try {
                    animateFling(rotationFlingDecay, initialRotationVelocity) { rotationDelta ->
                        val targetAngle = rotationAngle + rotationDelta
                        transform(
                            centroid = centroid,
                            rotationDelta = rotationDelta
                        )
                        rotationAngle == targetAngle
                    }
                } finally {
                    rotationRunning = false
                }
            }

            var zoomRunning = true
            launch {
                try {
                    animateFling(zoomFlingDecay, initialLogZoomVelocity) { logDelta ->
                        val zoomFactor = exp(logDelta)
                        val targetScale = zoomScale * zoomFactor
                        transform(
                            centroid = centroid,
                            zoomFactor = zoomFactor
                        )
                        zoomScale == targetScale || rotationRunning
                    }
                } finally {
                    zoomRunning = false
                }
            }

            val boundsChanging = { rotationRunning || zoomRunning }

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

        /**
         * A saver that saves the bounds and camera of a [LazyTransformableLayoutState]. The decay specs are not saved,
         * so restoring uses the ones given here.
         *
         * @param panFlingDecay decay animation spec for panning fling velocity
         * @param rotationFlingDecay decay animation spec for rotation fling velocity in degrees
         * @param zoomFlingDecay decay animation spec for zoom fling velocity in log scale
         */
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
                        centerOffset.x.value,
                        centerOffset.y.value,
                        rotationAngle,
                        zoomScale
                    )
                }
            },
            restore = { values ->
                LazyTransformableLayoutState(
                    initialLayoutBounds = DpRect(
                        left = values[0].dp,
                        top = values[1].dp,
                        right = values[2].dp,
                        bottom = values[3].dp
                    ),
                    initialRotationBounds = values[4]..values[5],
                    initialZoomBounds = values[6]..values[7],
                    initialCenterOffset = DpOffset(values[8].dp, values[9].dp),
                    initialRotationAngle = values[10],
                    initialZoomScale = values[11],
                    panFlingDecay = panFlingDecay,
                    rotationFlingDecay = rotationFlingDecay,
                    zoomFlingDecay = zoomFlingDecay
                )
            }
        )
    }
}
