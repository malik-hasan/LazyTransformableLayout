package oats.mobile.lazytransformablelayout

import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.FloatDecayAnimationSpec
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.generateDecayAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.extension.center
import oats.mobile.lazytransformablelayout.extension.radians
import oats.mobile.lazytransformablelayout.extension.toDp
import oats.mobile.lazytransformablelayout.extension.toLayoutAxes
import oats.mobile.lazytransformablelayout.extension.toPx
import oats.mobile.lazytransformablelayout.extension.toViewportAxes
import oats.mobile.lazytransformablelayout.extension.vertices
import oats.mobile.lazytransformablelayout.model.Parallelogram
import oats.mobile.lazytransformablelayout.model.Pivot
import oats.mobile.lazytransformablelayout.model.TransformAnimationValues
import oats.mobile.lazytransformablelayout.model.Viewport
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin

/**
 * The state of a [LazyTransformableLayout]: the [rotationAngle], [zoomScale] and [centerOffset] that
 * determine how the layout appears in the viewport
 *
 * Terms used in this documentation:
 * - **Layout space**: the space the Positionable items' bounds are in, in dp. [layoutBounds], [centerOffset]
 * and the bounds to fit in [snapToBounds] are in layout space.
 * - **Viewport**: the area the [LazyTransformableLayout] occupies on screen. Points and distances in the viewport,
 * such as a pan delta, are in px from its top-left corner, like pointer positions.
 * - **Content area**: the part of the viewport inside the layout's content padding. The [centerOffset] is placed
 * at its center, and fitting and clamping use it.
 *
 * @param initialLayoutBounds The bounds in layout space which can be panned into view.
 * Positionables are permitted to be placed outside of these bounds.
 * @param initialRotationBounds min and max rotation angle bounds in degrees
 * @param initialZoomBounds min and max zoom scale bounds
 * @param initialCenterOffset initial point in layout space to place at the center of the content area,
 * defaulting to the center of [initialLayoutBounds]
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

    private val Viewport.layoutBoundsPx
        get() = layoutBounds.toRect()

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

        val viewport = with(measureScope) {
            Viewport(density, fontScale, constraints, contentBounds)
        }

        if (this.viewport != viewport) {
            this.viewport = viewport

            clampAndSet(
                zoomScale = pendingFitBounds?.let { (bounds, margin) ->
                    pendingFitBounds = null
                    viewport.boundsFitScale(bounds, rotationAngle, margin)
                }
            )
        }
    }

    /**
     * Converts a point in layout space to where it currently is in the viewport
     *
     * @param position position in layout space
     * @return position in the viewport, or [Offset.Unspecified] before the layout is first measured
     */
    fun layoutToViewport(position: DpOffset) = viewport?.layoutToViewport(
        position = position,
        angle = rotationAngle,
        scale = zoomScale,
        center = centerOffset
    ) ?: Offset.Unspecified

    /**
     * Converts a point in the viewport, such as a pointer position, to the current point in layout space
     *
     * @param position position in the viewport
     * @return position in layout space, or [DpOffset.Unspecified] before the layout is first measured
     */
    fun viewportToLayout(position: Offset) = viewport?.viewportToLayout(
        position = position,
        angle = rotationAngle,
        scale = zoomScale,
        center = centerOffset
    ) ?: DpOffset.Unspecified

    private var previousCompositionBounds: Pair<Float, Parallelogram>? = null

    internal val compositionBounds by derivedStateOf(referentialEqualityPolicy()) {
        viewport?.run {
            val minItemDimension = 0.5f / zoomScale
            previousCompositionBounds?.takeIf { (previousMinItemDimension, previousBounds) ->
                minItemDimension * 1.19f >= previousMinItemDimension
                    && viewportBounds(128f) in previousBounds
            } ?: (minItemDimension to viewportBounds(256f))
                .also { previousCompositionBounds = it }
        }
    }

    private fun Viewport.viewportBounds(buffer: Float) = Parallelogram(
        size.toSize().toRect()
            .inflate(buffer)
            .vertices.map {
                viewportToLayout(it, rotationAngle, zoomScale, centerOffset)
            }
    )

    private val scaleBounds by derivedStateOf {
        viewport?.scaleBoundsAt(rotationAngle) ?: zoomBounds
    }

    private fun Viewport.scaleBoundsAt(rotationAngle: Float): ClosedFloatingPointRange<Float> {
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

    private val panningBounds by derivedStateOf {
        viewport?.panningBoundsAt(rotationAngle, zoomScale)
    }

    private fun Viewport.panningBoundsAt(rotationAngle: Float, zoomScale: Float): Parallelogram {
        val layoutBounds = layoutBoundsPx
        val (width, height) = contentBounds.size
        val startIndex = (-floor(rotationAngle / 90f).toInt() - 1).mod(4)
        fun vertex(index: Int) = layoutBounds
            .vertices[(startIndex + index) % 4]
            .toViewportAxes(zoomScale, rotationAngle)

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
            ).map { (it + contentCenter).toLayoutAxes(zoomScale, rotationAngle) }
        )
    }

    /**
     * Replaces any of the bounds, then clamps the rotation angle, zoom scale and center offset to them.
     * A null value keeps its current bounds. Cancels any ongoing fling or animation.
     *
     * @param layoutBounds the bounds in layout space which can be panned into view
     * @param rotationBounds min and max rotation angle bounds in degrees
     * @param zoomBounds min and max zoom scale bounds
     */
    fun updateBounds(
        layoutBounds: DpRect? = null,
        rotationBounds: ClosedFloatingPointRange<Float>? = null,
        zoomBounds: ClosedFloatingPointRange<Float>? = null
    ) {
        if (layoutBounds != null || rotationBounds != null || zoomBounds != null) {
            cancelAnimation()

            layoutBounds?.let {
                this.layoutBounds = it
            }

            rotationBounds?.let {
                this.rotationBounds = it
            }

            zoomBounds?.let {
                this.zoomBounds = it
            }

            clampAndSet()
        }
    }

    /**
     * The point in layout space currently at the center of the content area
     */
    var centerOffset by mutableStateOf(initialCenterOffset)
        private set

    private var Viewport.centerOffsetPx: Offset
        get() = centerOffset.toPx()
        set(value) {
            centerOffset = value.toDp()
        }

    private fun clampCenterOffset(center: Offset) = panningBounds?.clamp(center) ?: center

    private fun Offset.toViewportAxes() = toViewportAxes(zoomScale, rotationAngle)

    private fun Offset.toLayoutAxes() = toLayoutAxes(zoomScale, rotationAngle)

    internal val translation by derivedStateOf {
        viewport?.run {
            centerOffsetPx.toViewportAxes() - contentCenter
        } ?: Offset.Zero
    }

    /**
     * The rotation angle of the layout in degrees, clockwise
     */
    var rotationAngle by mutableFloatStateOf(clampRotationAngle(initialRotationAngle))
        private set

    private fun clampRotationAngle(rotationAngle: Float) = rotationAngle.coerceIn(rotationBounds)

    /**
     * The zoom scale of the layout, where 1 shows the layout at its size in dp
     */
    var zoomScale by mutableFloatStateOf(clampZoomScale(initialZoomScale))
        private set

    private fun clampZoomScale(zoomScale: Float) = zoomScale.coerceIn(scaleBounds)

    private fun clamp(
        rotationAngle: Float,
        zoomScale: Float,
        centerOffset: DpOffset
    ): Triple<Float, Float, DpOffset> {
        val clampedAngle = clampRotationAngle(rotationAngle)
        return viewport?.run {
            val clampedScale = zoomScale.coerceIn(scaleBoundsAt(clampedAngle))
            Triple(
                clampedAngle,
                clampedScale,
                panningBoundsAt(clampedAngle, clampedScale).clamp(centerOffset)
            )
        } ?: Triple(clampedAngle, zoomScale.coerceIn(zoomBounds), centerOffset)
    }

    private fun clampAndSet(
        rotationAngle: Float? = null,
        zoomScale: Float? = null,
        centerOffset: DpOffset? = null
    ): Array<Boolean> {
        val targetAngle = rotationAngle ?: this.rotationAngle
        val targetScale = zoomScale ?: this.zoomScale
        val targetCenter = centerOffset ?: this.centerOffset
        val (clampedAngle, clampedScale, clampedCenter) = clamp(
            rotationAngle = targetAngle,
            zoomScale = targetScale,
            centerOffset = targetCenter
        )

        this.rotationAngle = clampedAngle
        this.zoomScale = clampedScale
        this.centerOffset = clampedCenter

        return arrayOf(
            targetAngle != clampedAngle,
            targetScale != clampedScale,
            targetCenter != clampedCenter
        )
    }

    private fun Viewport.transform(
        pivot: Offset,
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

        centerOffsetPx = clampCenterOffset(
            centerFromPivot(
                viewportPivot = pivot,
                angle = newAngle,
                scale = newScale,
                layoutPivot = viewportToLayout(
                    position = pivot,
                    angle = previousAngle,
                    scale = previousScale,
                    center = centerOffset
                ).toDp()
            )
        )

        return panBy(-panDelta)
    }

    private fun Viewport.panBy(delta: Offset): Offset {
        val previousCenter = centerOffsetPx
        val newCenter = clampCenterOffset(
            previousCenter + delta.toLayoutAxes()
        )
        centerOffsetPx = newCenter
        return (newCenter - previousCenter).toViewportAxes()
    }

    /**
     * Rotates and zooms the layout about [pivot], then pans it, like a transform gesture. Each value is clamped
     * to its bounds. Cancels any ongoing or requested fling or animation.
     * Has no effect before the layout is first measured.
     *
     * This is useful for using continuous input such as gestures to control the layout state.
     *
     * See [animateBy] to animate the same change, or [requestAnimateBy] to animate it without suspending.
     * See [snapTo] and [animateTo] to set absolute values instead.
     *
     * @param pivot the point that stays fixed on screen while rotating and zooming
     * @param rotationDelta change in rotation angle in degrees
     * @param zoomFactor factor to multiply the zoom scale by
     * @param panDelta distance in pixels to move the content on screen
     * @param overscrollEffect the effect to apply any pan that goes past the panning bounds to.
     * This should be the same instance passed to the [LazyTransformableLayout] Composable, or null if no effect is desired.
     * @param nestedScrollSource the source of the transformation, reported to [overscrollEffect]
     */
    fun snapBy(
        pivot: Pivot = Pivot.ContentCenter,
        rotationDelta: Float = 0f,
        zoomFactor: Float = 1f,
        panDelta: Offset = Offset.Zero,
        overscrollEffect: OverscrollEffect? = null,
        nestedScrollSource: NestedScrollSource = NestedScrollSource.UserInput
    ) {
        viewport?.run {
            cancelAnimation()
            pendingAnimation = null

            val scaledPanDelta = panDelta / zoomFactor
            val viewportPivot = pivot.viewportPosition(rotationAngle, zoomScale, centerOffset)
            overscrollEffect?.applyToScroll(scaledPanDelta, nestedScrollSource) { scrollDelta ->
                transform(
                    pivot = viewportPivot,
                    rotationDelta = rotationDelta,
                    zoomFactor = zoomFactor,
                    panDelta = scrollDelta
                )
            } ?: transform(
                pivot = viewportPivot,
                rotationDelta = rotationDelta,
                zoomFactor = zoomFactor,
                panDelta = scaledPanDelta
            )
        }
    }

    private val animatable = Animatable(
        initialValue = TransformAnimationValues(),
        typeConverter = TwoWayConverter(
            convertToVector = { AnimationVector4D(
                it.rotationAngle,
                it.logZoomScale,
                it.layoutPivot.x.value,
                it.layoutPivot.y.value
            ) },
            convertFromVector = {
                TransformAnimationValues(
                    rotationAngle = it.v1,
                    logZoomScale = it.v2,
                    layoutPivot = DpOffset(it.v3.dp, it.v4.dp)
                )
            }
        )
    )
    private var previousViewportPivot = Offset.Zero

    private suspend fun Viewport.animate(
        viewportPivot: Offset,
        targetAngle: Float,
        targetScale: Float,
        targetCenter: DpOffset,
        animationSpec: AnimationSpec<TransformAnimationValues>,
        mutatePriority: MutatePriority
    ) = claimAnimation(mutatePriority) {
        animatable.run {
            snapTo(
                TransformAnimationValues(
                    rotationAngle = rotationAngle,
                    logZoomScale = ln(zoomScale),
                    layoutPivot = viewportToLayout(
                        position = viewportPivot,
                        angle = rotationAngle,
                        scale = zoomScale,
                        center = centerOffset
                    ).toDp()
                )
            )

            animateTo(
                targetValue = TransformAnimationValues(
                    rotationAngle = targetAngle,
                    logZoomScale = ln(targetScale),
                    layoutPivot = viewportToLayout(
                        position = viewportPivot,
                        angle = targetAngle,
                        scale = targetScale,
                        center = targetCenter
                    ).toDp()
                ),
                animationSpec = animationSpec,
                initialVelocity = (
                    if (isRunning) {
                        val pivotShift = (viewportPivot - previousViewportPivot).toLayoutAxes()
                        velocity.run {
                            TransformAnimationValues(
                                rotationAngle = rotationAngle,
                                logZoomScale = logZoomScale,
                                layoutPivot = layoutPivot + (
                                    -pivotShift * logZoomScale - Offset(-pivotShift.y, pivotShift.x) * rotationAngle.radians
                                ).toDp()
                            )
                        }
                    } else TransformAnimationValues()
                ).also { previousViewportPivot = viewportPivot }
            ) {
                val scale = exp(value.logZoomScale)
                clampAndSet(
                    rotationAngle = value.rotationAngle,
                    zoomScale = scale,
                    centerOffset = centerFromPivot(
                        viewportPivot = viewportPivot,
                        angle = value.rotationAngle,
                        scale = scale,
                        layoutPivot = value.layoutPivot
                    ).toDp()
                )
            }
        }
    }

    /**
     * Animates rotation and zoom about [pivot], then pans, with each value clamped to its bounds.
     * Cancels any other ongoing or requested fling or animation. Suspends until the layout is first measured.
     *
     * Must be called from a coroutine with a [androidx.compose.runtime.MonotonicFrameClock], such as one from
     * [androidx.compose.runtime.rememberCoroutineScope] or [androidx.compose.runtime.LaunchedEffect], since it animates
     * on composition frames. From other contexts, such as a ViewModel's scope, use [requestAnimateBy] instead.
     *
     * See [snapBy] to apply the same change immediately, or [requestAnimateBy] to animate it without suspending.
     *
     * @param pivot the point that stays fixed on screen while rotating and zooming
     * @param rotationDelta change in rotation angle in degrees
     * @param zoomFactor factor to multiply the zoom scale by
     * @param panDelta distance in px to move the point under [pivot] on screen, applied after rotating and zooming
     * @param additive if true, these deltas will be added onto any running animation, so repeated calls such as key repeats
     * accumulate instead of cutting each other short, else it replaces the existing animation.
     * Either way the running animation's velocity is continued.
     * @param animationSpec the spec animating the rotation angle, the zoom scale in log space and the [pivot]
     * in layout space, as one [TransformAnimationValues]
     * @param mutatePriority [MutatePriority.PreventUserInput] ignores gestures and keys until the animation ends,
     * instead of letting them cancel it
     */
    suspend fun animateBy(
        pivot: Pivot = Pivot.ContentCenter,
        rotationDelta: Float = 0f,
        zoomFactor: Float = 1f,
        panDelta: Offset = Offset.Zero,
        additive: Boolean = false,
        animationSpec: AnimationSpec<TransformAnimationValues> = spring(),
        mutatePriority: MutatePriority = MutatePriority.Default
    ) = with(snapshotFlow { viewport }.filterNotNull().first()) {
        val (startAngle, startScale, startCenter) = if (additive && animatable.isRunning) {
            animatable.targetValue.let { target ->
                val scale = exp(target.logZoomScale)
                Triple(
                    target.rotationAngle,
                    scale,
                    centerFromPivot(
                        viewportPivot = previousViewportPivot,
                        angle = target.rotationAngle,
                        scale = scale,
                        layoutPivot = target.layoutPivot
                    ).toDp()
                )
            }
        } else Triple(rotationAngle, zoomScale, centerOffset)

        val viewportPivot = pivot.viewportPosition(startAngle, startScale, startCenter)

        val (targetAngle, targetScale) = clamp(
            rotationAngle = startAngle + rotationDelta,
            zoomScale = startScale * zoomFactor,
            centerOffset = centerOffset
        )

        val (_, _, targetCenter) = clamp(
            rotationAngle = targetAngle,
            zoomScale = targetScale,
            centerOffset = centerFromPivot(
                viewportPivot = viewportPivot + panDelta,
                angle = targetAngle,
                scale = targetScale,
                layoutPivot = viewportToLayout(
                    position = viewportPivot,
                    angle = startAngle,
                    scale = startScale,
                    center = startCenter
                ).toDp()
            ).toDp()
        )

        animate(
            viewportPivot = viewportPivot,
            targetAngle = targetAngle,
            targetScale = targetScale,
            targetCenter = targetCenter,
            animationSpec = animationSpec,
            mutatePriority = mutatePriority
        )
    }

    private var pendingAnimation by mutableStateOf<Pair<MutatePriority, suspend () -> Unit>?>(null)

    /**
     * Animates rotation and zoom about [pivot], then pans, with each value clamped to its bounds. Doesn't suspend,
     * so it can be called outside a composition-bound coroutine, such as from a ViewModel. The animation starts on the
     * next frame while the layout is composed, replacing any earlier request, and a [snapTo], [snapBy] or
     * [snapToBounds] before it starts discards the request. Once it starts, it cancels any other ongoing fling
     * or animation.
     *
     * See [animateBy] to animate the same change in a coroutine, or [snapBy] to apply it immediately.
     *
     * @param pivot the point that stays fixed on screen while rotating and zooming
     * @param rotationDelta change in rotation angle in degrees
     * @param zoomFactor factor to multiply the zoom scale by
     * @param panDelta distance in px to move the point under [pivot] on screen, applied after rotating and zooming
     * @param additive false animates from the current state, interrupting any running animation.
     * true adds this change to where a running animation is heading, so repeated calls such as key repeats
     * accumulate instead of cutting each other short. Either way the running animation's velocity is kept.
     * @param animationSpec the spec animating the rotation angle, the zoom scale in log space and the [pivot]
     * in layout space, as one [TransformAnimationValues]
     * @param mutatePriority [MutatePriority.PreventUserInput] ignores gestures and keys until the animation ends,
     * instead of letting them cancel it
     */
    fun requestAnimateBy(
        pivot: Pivot = Pivot.ContentCenter,
        rotationDelta: Float = 0f,
        zoomFactor: Float = 1f,
        panDelta: Offset = Offset.Zero,
        additive: Boolean = false,
        animationSpec: AnimationSpec<TransformAnimationValues> = spring(),
        mutatePriority: MutatePriority = MutatePriority.Default
    ) {
        pendingAnimation = mutatePriority to {
            animateBy(
                pivot = pivot,
                rotationDelta = rotationDelta,
                zoomFactor = zoomFactor,
                panDelta = panDelta,
                additive = additive,
                animationSpec = animationSpec,
                mutatePriority = mutatePriority
            )
        }
    }

    /**
     * Snaps the state to the given values, clamped to their bounds. A null value keeps its current value.
     * Cancels any ongoing fling or animation.
     *
     * See [animateTo] to animate to the same values, or [requestAnimateTo] to animate to them without suspending.
     *
     * @param rotationAngle rotation angle in degrees
     * @param zoomScale zoom scale
     * @param centerOffset point in layout space to place at the center of the content area
     * @return for rotation angle, zoom scale and center offset, whether the value was clamped
     */
    fun snapTo(
        rotationAngle: Float? = null,
        zoomScale: Float? = null,
        centerOffset: DpOffset? = null
    ): Array<Boolean> {
        cancelAnimation()
        pendingAnimation = null
        pendingFitBounds = null
        return clampAndSet(rotationAngle, zoomScale, centerOffset)
    }

    /**
     * Animates the state to the given values, clamped to their bounds. A null value keeps its current value.
     * Calling it again while it is running retargets the animation and keeps its velocity.
     * Cancels any other ongoing fling or animation, and is canceled by user input unless [mutatePriority] prevents it.
     * Suspends until the layout is first measured.
     *
     *
     * Must be called from a coroutine with a [androidx.compose.runtime.MonotonicFrameClock], such as one from
     * [androidx.compose.runtime.rememberCoroutineScope] or [androidx.compose.runtime.LaunchedEffect], since it animates
     * on composition frames. From other contexts, such as a ViewModel's scope, use [requestAnimateTo] instead.
     *
     * See [snapTo] to set the same values immediately, or [requestAnimateTo] to animate to them without suspending.
     *
     * @param rotationAngle rotation angle in degrees
     * @param zoomScale zoom scale
     * @param centerOffset point in layout space to place at the center of the content area
     * @param pivot the point that stays fixed on screen while rotating and zooming
     * @param animationSpec the spec animating the rotation angle, the zoom scale in log space and the [pivot]
     * in layout space, as one [TransformAnimationValues]
     * @param mutatePriority [MutatePriority.PreventUserInput] ignores gestures and keys until the animation ends,
     * instead of letting them cancel it
     * @return for rotation angle, zoom scale and center offset, whether the value was clamped
     */
    suspend fun animateTo(
        rotationAngle: Float? = null,
        zoomScale: Float? = null,
        centerOffset: DpOffset? = null,
        pivot: Pivot = Pivot.ContentCenter,
        animationSpec: AnimationSpec<TransformAnimationValues> = spring(),
        mutatePriority: MutatePriority = MutatePriority.Default
    ): Array<Boolean> {
        val startAngle = this.rotationAngle
        val startScale = this.zoomScale
        val startCenter = this.centerOffset
        with(snapshotFlow { viewport }.filterNotNull().first()) {
            val viewportPivot = pivot.viewportPosition(
                angle = startAngle,
                scale = startScale,
                center = startCenter
            )

            val (targetAngle, targetScale) = clamp(
                rotationAngle = rotationAngle ?: startAngle,
                zoomScale = zoomScale ?: startScale,
                centerOffset = startCenter
            )

            val (_, _, targetCenter) = clamp(
                rotationAngle = targetAngle,
                zoomScale = targetScale,
                centerOffset = centerOffset ?: centerFromPivot(
                    viewportPivot = viewportPivot,
                    angle = targetAngle,
                    scale = targetScale,
                    layoutPivot = viewportToLayout(
                        position = viewportPivot,
                        angle = startAngle,
                        scale = startScale,
                        center = startCenter
                    ).toDp()
                ).toDp()
            )

            animate(
                viewportPivot = viewportPivot,
                targetAngle = targetAngle,
                targetScale = targetScale,
                targetCenter = targetCenter,
                animationSpec = animationSpec,
                mutatePriority = mutatePriority
            )
        }

        return clampAndSet(rotationAngle, zoomScale, centerOffset)
    }

    /**
     * Animates the state to the given values, clamped to their bounds.
     * Doesn't suspend, so it can be called outside a composition-bound coroutine, such as from a ViewModel.
     * The animation starts on the next frame while the layout is composed,
     * replacing any earlier request, and a [snapTo], [snapBy] or [snapToBounds] before it starts discards the request.
     * Once it starts, it cancels any other ongoing fling or animation, continuing the velocity of a running animation.
     *
     * See [animateTo] to animate to the same values in a coroutine, or [snapTo] to set them immediately.
     *
     * @param rotationAngle rotation angle in degrees
     * @param zoomScale zoom scale
     * @param centerOffset point in layout space to place at the center of the content area
     * @param pivot the point that stays fixed on screen while rotating and zooming
     * @param animationSpec the spec animating the rotation angle, the zoom scale in log space and the [pivot]
     * in layout space, as one [TransformAnimationValues]
     * @param mutatePriority [MutatePriority.PreventUserInput] ignores gestures and keys until the animation ends,
     * instead of letting them cancel it
     */
    fun requestAnimateTo(
        rotationAngle: Float? = null,
        zoomScale: Float? = null,
        centerOffset: DpOffset? = null,
        pivot: Pivot = Pivot.ContentCenter,
        animationSpec: AnimationSpec<TransformAnimationValues> = spring(),
        mutatePriority: MutatePriority = MutatePriority.Default
    ) {
        pendingAnimation = mutatePriority to {
            animateTo(rotationAngle, zoomScale, centerOffset, pivot, animationSpec, mutatePriority)
        }
    }

    private var pendingFitBounds: Pair<DpRect, Dp>? = null

    /**
     * Snaps to center [bounds] in the content area, at the largest zoom scale that fits it at the target
     * rotation angle, with [margin] to spare. The values are clamped to their bounds.
     *
     * See [animateToBounds] to animate to the same fit, or [requestAnimateToBounds] to animate to it without suspending.
     *
     * @param bounds area in layout space to fit in the content area
     * @param rotationAngle rotation angle in degrees, or null to keep the current rotation angle
     * @param margin space to keep between [bounds] and each edge of the content area
     * @return for rotation angle, zoom scale and center offset, whether the value was clamped
     */
    fun snapToBounds(
        bounds: DpRect,
        rotationAngle: Float? = null,
        margin: Dp = 0.dp
    ) = viewport?.let {
        snapTo(
            rotationAngle = rotationAngle,
            zoomScale = it.boundsFitScale(
                bounds = bounds,
                rotationAngle = clampRotationAngle(rotationAngle ?: this.rotationAngle),
                margin = margin
            ),
            centerOffset = bounds.center
        )
    } ?: snapTo(rotationAngle, centerOffset = bounds.center).also {
        pendingFitBounds = bounds to margin
    }

    /**
     * Animates to center [bounds] in the content area, at the largest zoom scale that fits them at the target
     * rotation angle, with [margin] to spare. Continues the velocity of any running animation.
     * Cancels any other ongoing fling or animation, and is canceled by user
     * input unless [mutatePriority] prevents it.
     * Suspends until the layout is first measured.
     *
     * Must be called from a coroutine with a [androidx.compose.runtime.MonotonicFrameClock], such as one from
     * [androidx.compose.runtime.rememberCoroutineScope] or [androidx.compose.runtime.LaunchedEffect], since it animates
     * on composition frames. From other contexts, such as a ViewModel's scope, use [requestAnimateToBounds] instead.
     *
     * See [snapToBounds] to fit immediately, or [requestAnimateToBounds] to animate without suspending.
     *
     * @param bounds area in layout space to fit in the content area
     * @param rotationAngle rotation angle in degrees, or null to keep the current rotation angle
     * @param margin space to keep between [bounds] and each edge of the content area
     * @param animationSpec the spec animating the rotation angle, the zoom scale in log space and the center of
     * the content area in layout space, as one [TransformAnimationValues]
     * @param mutatePriority [MutatePriority.PreventUserInput] ignores gestures and keys until the animation ends,
     * instead of letting them cancel it
     * @return for rotation angle, zoom scale and center offset, whether the value was within bounds, or null if it was not updated
     */
    suspend fun animateToBounds(
        bounds: DpRect,
        rotationAngle: Float? = null,
        margin: Dp = 0.dp,
        animationSpec: AnimationSpec<TransformAnimationValues> = spring(),
        mutatePriority: MutatePriority = MutatePriority.Default
    ) = animateTo(
        rotationAngle = rotationAngle,
        zoomScale = snapshotFlow { viewport }.filterNotNull().first().boundsFitScale(
            bounds = bounds,
            rotationAngle = clampRotationAngle(rotationAngle ?: this.rotationAngle),
            margin = margin
        ),
        centerOffset = bounds.center,
        animationSpec = animationSpec,
        mutatePriority = mutatePriority
    )

    /**
     * Animates to center [bounds] in the content area, at the largest zoom scale that fits them at the target
     * rotation angle, with [margin] to spare. The values are clamped to their bounds. Doesn't suspend, so it can be
     * called outside a composition-bound coroutine, such as from a ViewModel. The animation starts once the layout is
     * composed and measured, replacing any earlier request, and a [snapTo], [snapBy] or [snapToBounds] before it
     * starts discards the request. Once it starts, it cancels any other ongoing fling or animation, continuing the
     * velocity of a running animation.
     *
     * See [animateToBounds] to animate to the same fit in a coroutine, or [snapToBounds] to fit immediately.
     *
     * @param bounds area in layout space to fit in the content area
     * @param rotationAngle rotation angle in degrees, or null to keep the current rotation angle
     * @param margin space to keep between [bounds] and each edge of the content area
     * @param animationSpec the spec animating the rotation angle, the zoom scale in log space and the center of
     * the content area in layout space, as one [TransformAnimationValues]
     * @param mutatePriority [MutatePriority.PreventUserInput] ignores gestures and keys until the animation ends,
     * instead of letting them cancel it
     */
    fun requestAnimateToBounds(
        bounds: DpRect,
        rotationAngle: Float? = null,
        margin: Dp = 0.dp,
        animationSpec: AnimationSpec<TransformAnimationValues> = spring(),
        mutatePriority: MutatePriority = MutatePriority.Default
    ) {
        pendingAnimation = mutatePriority to {
            animateToBounds(bounds, rotationAngle, margin, animationSpec, mutatePriority)
        }
    }

    internal suspend fun runPendingAnimations() = coroutineScope {
        var previous: Job? = null
        snapshotFlow { pendingAnimation }
            .filterNotNull()
            .collect { (_, animation) ->
                pendingAnimation = null
                if (previous != this@LazyTransformableLayoutState.animation) previous?.cancel()
                previous = launch(start = CoroutineStart.UNDISPATCHED) { animation() }
            }
    }

    private var animation: Job? = null
    private var mutatePriority = MutatePriority.Default

    internal val isUserInputBlocked
        get() = pendingAnimation?.first == MutatePriority.PreventUserInput
            || animation?.isActive == true && mutatePriority == MutatePriority.PreventUserInput

    /**
     * Cancels any ongoing fling or animation, leaving the state where it is
     */
    fun cancelAnimation() = animation?.cancel()

    private suspend fun <R> claimAnimation(
        priority: MutatePriority = MutatePriority.Default,
        block: suspend () -> R
    ): R {
        val job = currentCoroutineContext().job
        if (animation != job) animation?.cancel()
        animation = job
        mutatePriority = priority
        pendingAnimation = null
        return try {
            block()
        } finally {
            if (animation == job) animation = null
        }
    }

    internal suspend fun fling(
        initialRotationVelocity: Float = 0f,
        initialLogZoomVelocity: Float = 0f,
        initialPanVelocity: Velocity = Velocity.Zero,
        viewportPivot: Offset = Offset.Unspecified,
        overscrollEffect: OverscrollEffect? = null
    ) = viewport?.run {
        if ((initialRotationVelocity == 0f && initialLogZoomVelocity == 0f && initialPanVelocity == Velocity.Zero)
            || isUserInputBlocked
        ) {
            overscrollEffect?.applyToFling(Velocity.Zero) { Velocity.Zero }
        } else claimAnimation {
            coroutineScope {
                val viewportPivot = viewportPivot.takeIf { it.isSpecified } ?: contentCenter

                var rotationRunning = true
                launch {
                    try {
                        animateFling(
                            rotationFlingDecay,
                            initialRotationVelocity
                        ) { rotationDelta ->
                            val targetAngle = rotationAngle + rotationDelta
                            transform(
                                pivot = viewportPivot,
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
                                pivot = viewportPivot,
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
    }

    private suspend fun Viewport.flingX(xVelocity: Float, boundsChanging: () -> Boolean) = Velocity(
        x = flingPan(xVelocity, boundsChanging) {
            delta -> panBy(Offset(delta, 0f)).x
        },
        y = 0f
    )

    private suspend fun Viewport.flingY(yVelocity: Float, boundsChanging: () -> Boolean) = Velocity(
        x = 0f,
        y = flingPan(yVelocity, boundsChanging) {
            delta -> panBy(Offset(0f, delta)).y
        }
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
         * A saver that saves the bounds, rotation angle, zoom scale and center offset of a [LazyTransformableLayoutState].
         * The decay specs are not saved, so restoring uses the ones given here.
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
