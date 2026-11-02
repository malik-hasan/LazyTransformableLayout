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
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.extension.center
import oats.mobile.lazytransformablelayout.extension.radians
import oats.mobile.lazytransformablelayout.extension.toDp
import oats.mobile.lazytransformablelayout.extension.toLayoutAxes
import oats.mobile.lazytransformablelayout.extension.toPx
import oats.mobile.lazytransformablelayout.extension.toViewportAxes
import oats.mobile.lazytransformablelayout.model.Geometry
import oats.mobile.lazytransformablelayout.model.Parallelogram
import oats.mobile.lazytransformablelayout.model.Pivot
import oats.mobile.lazytransformablelayout.model.TransformAnimationValues
import kotlin.math.exp
import kotlin.math.ln

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
    private var layoutBounds by mutableStateOf(initialLayoutBounds)

    private var rotationBounds by mutableStateOf(initialRotationBounds)

    private var zoomBounds by mutableStateOf(initialZoomBounds)

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

    /**
     * Replaces any of the bounds, then clamps the rotation angle, zoom scale and center offset to them.
     * A null value keeps its current bounds.
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

    private var geometry by mutableStateOf<Geometry?>(null)

    private var pendingFitBounds: Pair<DpRect, Dp>? = null

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

        val geometry = with(measureScope) {
            Geometry(density, fontScale, constraints, contentBounds)
        }

        if (this.geometry != geometry) {
            this.geometry = geometry

            clampAndSet(
                scale = pendingFitBounds?.let { (bounds, margin) ->
                    pendingFitBounds = null
                    geometry.boundsFitScale(bounds, rotationAngle, margin)
                } ?: zoomScale
            )
        }
    }

    private val scaleBounds by derivedStateOf {
        geometry?.scaleBoundsAt(layoutBounds, zoomBounds, rotationAngle) ?: zoomBounds
    }

    private val panningBounds by derivedStateOf {
        geometry?.panningBoundsAt(layoutBounds, rotationAngle, zoomScale)
    }

    /**
     * The point in layout space currently at the center of the content area
     */
    var centerOffset by mutableStateOf(initialCenterOffset)
        private set

    /**
     * The rotation angle of the layout in degrees, clockwise
     */
    var rotationAngle by mutableFloatStateOf(clampAngle(initialRotationAngle))
        private set

    /**
     * The zoom scale of the layout, where 1 shows the layout at its size in dp
     */
    var zoomScale by mutableFloatStateOf(clampScale(initialZoomScale))
        private set

    private fun clampAngle(angle: Float) = angle.coerceIn(rotationBounds)

    private fun clampScale(scale: Float) = scale.coerceIn(scaleBounds)

    private fun clampScale(angle: Float, scale: Float) =
        scale.coerceIn(geometry?.scaleBoundsAt(layoutBounds, zoomBounds, angle) ?: zoomBounds)

    private fun clampCenter(center: Offset) = panningBounds?.clamp(center) ?: center

    private fun Geometry.clampCenter(angle: Float, scale: Float, center: Offset) =
        panningBoundsAt(layoutBounds, angle, scale).clamp(center)

    private fun clampAndSet(
        angle: Float = rotationAngle,
        scale: Float = zoomScale,
        center: DpOffset = centerOffset
    ) {
        val clampedAngle = clampAngle(angle)
        val clampedScale = clampScale(clampedAngle, scale)
        rotationAngle = clampedAngle
        zoomScale = clampedScale
        centerOffset = geometry?.run {
            clampCenter(angle, scale, center.toPx()).toDp()
        } ?: center
    }

    private var Geometry.centerOffsetPx: Offset
        get() = centerOffset.toPx()
        set(value) {
            centerOffset = value.toDp()
        }

    private fun Offset.toViewportAxes() = toViewportAxes(zoomScale, rotationAngle)

    private fun Offset.toLayoutAxes() = toLayoutAxes(zoomScale, rotationAngle)

    /**
     * Converts a point in layout space to where it currently is in the viewport
     *
     * @param position position in layout space
     * @return position in the viewport, or [Offset.Unspecified] before the layout is first measured
     */
    fun layoutToViewport(position: DpOffset) = geometry?.layoutToViewport(
        layoutPosition = position,
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
    fun viewportToLayout(position: Offset) = geometry?.run {
        viewportToLayout(
            viewportPosition = position,
            angle = rotationAngle,
            scale = zoomScale,
            center = centerOffset
        )
    } ?: DpOffset.Unspecified

    internal val translation by derivedStateOf {
        geometry?.run {
            centerOffsetPx.toViewportAxes() - contentCenter
        } ?: Offset.Zero
    }

    private var previousCompositionBounds: Pair<Float, Parallelogram>? = null

    internal val compositionBounds by derivedStateOf(referentialEqualityPolicy()) {
        geometry?.run {
            val minItemDimension = 0.5f / zoomScale
            previousCompositionBounds?.takeIf { (previousMinItemDimension, previousBounds) ->
                minItemDimension * 1.19f >= previousMinItemDimension
                    && viewportBounds(128f, rotationAngle, zoomScale, centerOffsetPx) in previousBounds
            } ?: (minItemDimension to viewportBounds(256f, rotationAngle, zoomScale, centerOffsetPx))
                .also { previousCompositionBounds = it }
        }
    }

    /**
     * Rotates and zooms the layout about [pivot], then pans it, like a transform gesture.
     * Each value is clamped to its bounds. Cancels any ongoing or requested fling or animation of the same or lower
     * [mutatePriority], and does nothing if one of higher priority is running or requested.
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
     * @param panDelta distance in pixels to move the content on screen. It is divided by [zoomFactor] before it is
     * applied, so the on-screen pan stays comparable across zoom scales.
     * @param overscrollEffect the effect to apply any pan that goes past the panning bounds to.
     * This should be the same instance passed to the [LazyTransformableLayout] Composable, or null if no effect is desired.
     * @param nestedScrollSource the source of the transformation, reported to [overscrollEffect]
     * @param mutatePriority the priority of this change, which is ignored while an animation or request of higher
     * priority is active
     */
    fun snapBy(
        pivot: Pivot = Pivot.ContentCenter,
        rotationDelta: Float = 0f,
        zoomFactor: Float = 1f,
        panDelta: Offset = Offset.Zero,
        overscrollEffect: OverscrollEffect? = null,
        nestedScrollSource: NestedScrollSource = NestedScrollSource.UserInput,
        mutatePriority: MutatePriority = MutatePriority.Default
    ) {
        geometry?.run {
            snapBy(
                viewportPivot = pivot.viewportPosition(rotationAngle, zoomScale, centerOffset),
                rotationDelta = rotationDelta,
                zoomFactor = zoomFactor,
                panDelta = panDelta,
                overscrollEffect = overscrollEffect,
                nestedScrollSource = nestedScrollSource,
                mutatePriority = mutatePriority
            )
        }
    }

    internal fun snapBy(
        viewportPivot: Offset,
        rotationDelta: Float = 0f,
        zoomFactor: Float = 1f,
        panDelta: Offset = Offset.Zero,
        overscrollEffect: OverscrollEffect? = null,
        nestedScrollSource: NestedScrollSource = NestedScrollSource.UserInput,
        mutatePriority: MutatePriority = MutatePriority.Default
    ) = geometry?.takeIf { animationCoordinator.tryCancel(mutatePriority) }?.run {

        val transform = { panDelta: Offset ->
            transform(
                pivot = if (viewportPivot.isSpecified) viewportPivot else contentCenter,
                rotationDelta = rotationDelta,
                zoomFactor = zoomFactor,
                panDelta = panDelta
            )
        }

        val scaledPanDelta = panDelta / zoomFactor
        overscrollEffect?.applyToScroll(
            delta = scaledPanDelta,
            source = nestedScrollSource,
            performScroll = transform
        ) ?: transform(scaledPanDelta)
    }

    /**
     * Snaps the state to the given values, clamped to their bounds. A null value keeps its current value.
     * Cancels any ongoing or requested fling or animation of the same or lower
     * [mutatePriority], and does nothing if one of higher priority is running or requested.
     *
     * See [animateTo] to animate to the same values, or [requestAnimateTo] to animate to them without suspending.
     *
     * @param rotationAngle rotation angle in degrees
     * @param zoomScale zoom scale
     * @param centerOffset point in layout space to place at the center of the content area
     * @param mutatePriority the priority of this change, which is ignored while an animation or request of higher
     * priority is active
     */
    fun snapTo(
        rotationAngle: Float? = null,
        zoomScale: Float? = null,
        centerOffset: DpOffset? = null,
        mutatePriority: MutatePriority = MutatePriority.Default
    ) {
        if (!animationCoordinator.tryCancel(mutatePriority)) return
        pendingFitBounds = null
        clampAndSet(
            angle = rotationAngle ?: this.rotationAngle,
            scale = zoomScale ?: this.zoomScale,
            center = centerOffset ?: this.centerOffset
        )
    }

    /**
     * Snaps to center [bounds] in the content area, at the largest zoom scale that fits it at the target
     * rotation angle, with [margin] to spare. The values are clamped to their bounds.
     * Cancels any ongoing or requested fling or animation of the same or lower
     * [mutatePriority], and does nothing if one of higher priority is running or requested.
     *
     * See [animateToBounds] to animate to the same fit, or [requestAnimateToBounds] to animate to it without suspending.
     *
     * @param bounds area in layout space to fit in the content area
     * @param rotationAngle rotation angle in degrees, or null to keep the current rotation angle
     * @param margin absolute Dp space to keep between [bounds] and each edge of the content area, independent of zoom
     * @param mutatePriority the priority of this change, which is ignored while an animation or request of higher
     * priority is active
     */
    fun snapToBounds(
        bounds: DpRect,
        rotationAngle: Float? = null,
        margin: Dp = 0.dp,
        mutatePriority: MutatePriority = MutatePriority.Default
    ) {
        if (!animationCoordinator.tryCancel(mutatePriority)) return
        geometry?.run {
            snapTo(
                rotationAngle = rotationAngle,
                zoomScale = boundsFitScale(
                    bounds = bounds,
                    rotationAngle = clampAngle(rotationAngle ?: this@LazyTransformableLayoutState.rotationAngle),
                    margin = margin
                ),
                centerOffset = bounds.center,
                mutatePriority = mutatePriority
            )
        } ?: run {
            snapTo(rotationAngle, centerOffset = bounds.center, mutatePriority = mutatePriority)
            pendingFitBounds = bounds to margin
        }
    }

    private fun Geometry.transform(
        pivot: Offset,
        rotationDelta: Float = 0f,
        zoomFactor: Float = 1f,
        panDelta: Offset = Offset.Zero
    ): Offset {
        val previousAngle = rotationAngle
        val newAngle = clampAngle(previousAngle + rotationDelta)
        rotationAngle = newAngle

        val previousScale = zoomScale
        val newScale = clampScale(previousScale * zoomFactor)
        zoomScale = newScale

        centerOffsetPx = clampCenter(
            centerPreservingPivot(
                viewportPivot = pivot,
                angle = newAngle,
                scale = newScale,
                fromViewportPivot = pivot,
                fromAngle = previousAngle,
                fromScale = previousScale,
                fromCenter = centerOffsetPx
            )
        )

        return panBy(-panDelta)
    }

    private fun Geometry.panBy(delta: Offset): Offset {
        val previousCenter = centerOffsetPx
        val newCenter = clampCenter(previousCenter + delta.toLayoutAxes())
        centerOffsetPx = newCenter
        return (newCenter - previousCenter).toViewportAxes()
    }

    /**
     * Animates rotation and zoom about [pivot], then pans, with each value clamped to its bounds.
     * Cancels any ongoing or requested fling or animation of the same or lower [mutatePriority], and is canceled by
     * user input unless [mutatePriority] prevents it. Returns without animating if one of higher priority is running.
     * Suspends until the layout is first measured.
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
     * @param panDelta distance in px to move the point under [pivot] on screen, applied after rotating and zooming.
     * It is divided by [zoomFactor] before it is applied, so the on-screen pan stays comparable across zoom scales.
     * @param animationSpec the spec animating the rotation angle, the zoom scale in log space and the [pivot]
     * in layout space, as one [TransformAnimationValues]
     * @param mutatePriority the priority of this animation, which only animations and requests of the same or higher
     * priority can interrupt. [MutatePriority.PreventUserInput] also ignores gestures and keys until the animation
     * ends, instead of letting them cancel it
     */
    suspend fun animateBy(
        pivot: Pivot = Pivot.ContentCenter,
        rotationDelta: Float = 0f,
        zoomFactor: Float = 1f,
        panDelta: Offset = Offset.Zero,
        animationSpec: AnimationSpec<TransformAnimationValues> = spring(),
        mutatePriority: MutatePriority = MutatePriority.Default
    ) {
        with(snapshotFlow { geometry }.filterNotNull().first()) {
            val startAngle = rotationAngle
            val startScale = zoomScale
            val startCenter = centerOffsetPx

            animate(
                pivot = pivot,
                startAngle = startAngle,
                startScale = startScale,
                startCenter = startCenter,
                targetAngle = startAngle + rotationDelta,
                targetScale = startScale * zoomFactor,
                targetCenter = Offset.Unspecified,
                panDelta = panDelta / zoomFactor,
                animationSpec = animationSpec,
                mutatePriority = mutatePriority
            )
        }
    }

    /**
     * Animates the state to the given values, clamped to their bounds. A null value keeps its current value.
     * Calling it again while it is running retargets the animation and keeps its velocity.
     * Cancels any ongoing or requested fling or animation of the same or lower [mutatePriority], and is canceled by
     * user input unless [mutatePriority] prevents it. Returns without animating if one of higher priority is running.
     * Suspends until the layout is first measured.
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
     * @param mutatePriority the priority of this animation, which only animations and requests of the same or higher
     * priority can interrupt. [MutatePriority.PreventUserInput] also ignores gestures and keys until the animation
     * ends, instead of letting them cancel it
     */
    suspend fun animateTo(
        rotationAngle: Float? = null,
        zoomScale: Float? = null,
        centerOffset: DpOffset? = null,
        pivot: Pivot = Pivot.ContentCenter,
        animationSpec: AnimationSpec<TransformAnimationValues> = spring(),
        mutatePriority: MutatePriority = MutatePriority.Default
    ) {
        with(snapshotFlow { geometry }.filterNotNull().first()) {
            val startAngle = this@LazyTransformableLayoutState.rotationAngle
            val startScale = this@LazyTransformableLayoutState.zoomScale
            val startCenter = this@LazyTransformableLayoutState.centerOffset
            val startCenterPx = startCenter.toPx()

            animate(
                pivot = pivot,
                startAngle = startAngle,
                startScale = startScale,
                startCenter = startCenterPx,
                targetAngle = rotationAngle ?: startAngle,
                targetScale = zoomScale ?: startScale,
                targetCenter = centerOffset?.toPx() ?: Offset.Unspecified,
                panDelta = Offset.Zero,
                animationSpec = animationSpec,
                mutatePriority = mutatePriority
            )
        }
    }

    /**
     * Animates to center [bounds] in the content area, at the largest zoom scale that fits them at the target
     * rotation angle, with [margin] to spare. Continues the velocity of any running animation.
     * Cancels any ongoing or requested fling or animation of the same or lower [mutatePriority], and is canceled by
     * user input unless [mutatePriority] prevents it. Returns without animating if one of higher priority is running.
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
     * @param margin absolute Dp space to keep between [bounds] and each edge of the content area, independent of zoom
     * @param animationSpec the spec animating the rotation angle, the zoom scale in log space and the center of
     * the content area in layout space, as one [TransformAnimationValues]
     * @param mutatePriority the priority of this animation, which only animations and requests of the same or higher
     * priority can interrupt. [MutatePriority.PreventUserInput] also ignores gestures and keys until the animation
     * ends, instead of letting them cancel it
     */
    suspend fun animateToBounds(
        bounds: DpRect,
        rotationAngle: Float? = null,
        margin: Dp = 0.dp,
        animationSpec: AnimationSpec<TransformAnimationValues> = spring(),
        mutatePriority: MutatePriority = MutatePriority.Default
    ) = animateTo(
        rotationAngle = rotationAngle,
        zoomScale = snapshotFlow { geometry }.filterNotNull().first().boundsFitScale(
            bounds = bounds,
            rotationAngle = clampAngle(rotationAngle ?: this.rotationAngle),
            margin = margin
        ),
        centerOffset = bounds.center,
        animationSpec = animationSpec,
        mutatePriority = mutatePriority
    )

    /**
     * Animates rotation and zoom about [pivot], then pans, with each value clamped to its bounds. Doesn't suspend,
     * so it can be called outside a composition-bound coroutine, such as from a ViewModel. The animation starts on the
     * next frame while the layout is composed, replacing any earlier request of the same or lower [mutatePriority].
     * It is dropped if an earlier request has a higher one. [snapTo], [snapBy] or [snapToBounds] before it starts,
     * discards the request. Once it starts, it cancels any ongoing fling or animation of the same or lower priority,
     * or is skipped if one of higher priority is running.
     *
     * See [animateBy] to animate the same change in a coroutine, or [snapBy] to apply it immediately.
     *
     * @param pivot the point that stays fixed on screen while rotating and zooming
     * @param rotationDelta change in rotation angle in degrees
     * @param zoomFactor factor to multiply the zoom scale by
     * @param panDelta distance in px to move the point under [pivot] on screen, applied after rotating and zooming.
     * It is divided by [zoomFactor] before it is applied, so the on-screen pan stays comparable across zoom scales.
     * @param animationSpec the spec animating the rotation angle, the zoom scale in log space and the [pivot]
     * in layout space, as one [TransformAnimationValues]
     * @param mutatePriority the priority of this animation, which only animations and requests of the same or higher
     * priority can interrupt. [MutatePriority.PreventUserInput] also ignores gestures and keys until the animation
     * ends, instead of letting them cancel it
     */
    fun requestAnimateBy(
        pivot: Pivot = Pivot.ContentCenter,
        rotationDelta: Float = 0f,
        zoomFactor: Float = 1f,
        panDelta: Offset = Offset.Zero,
        animationSpec: AnimationSpec<TransformAnimationValues> = spring(),
        mutatePriority: MutatePriority = MutatePriority.Default
    ) = animationCoordinator.request(mutatePriority) {
        animateBy(
            pivot = pivot,
            rotationDelta = rotationDelta,
            zoomFactor = zoomFactor,
            panDelta = panDelta,
            animationSpec = animationSpec,
            mutatePriority = mutatePriority
        )
    }

    /**
     * Animates the state to the given values, clamped to their bounds.
     * Doesn't suspend, so it can be called outside a composition-bound coroutine, such as from a ViewModel.
     * The animation starts on the next frame while the layout is composed,
     * replacing any earlier request of the same or lower [mutatePriority]. It is dropped if an earlier request has a
     * higher one. [snapTo], [snapBy] or [snapToBounds] before it starts, discards the request.
     * Once it starts, it cancels any ongoing fling or animation of the same or lower priority, continuing the
     * velocity of a running animation, or is skipped if one of higher priority is running.
     *
     * See [animateTo] to animate to the same values in a coroutine, or [snapTo] to set them immediately.
     *
     * @param rotationAngle rotation angle in degrees
     * @param zoomScale zoom scale
     * @param centerOffset point in layout space to place at the center of the content area
     * @param pivot the point that stays fixed on screen while rotating and zooming
     * @param animationSpec the spec animating the rotation angle, the zoom scale in log space and the [pivot]
     * in layout space, as one [TransformAnimationValues]
     * @param mutatePriority the priority of this animation, which only animations and requests of the same or higher
     * priority can interrupt. [MutatePriority.PreventUserInput] also ignores gestures and keys until the animation
     * ends, instead of letting them cancel it
     */
    fun requestAnimateTo(
        rotationAngle: Float? = null,
        zoomScale: Float? = null,
        centerOffset: DpOffset? = null,
        pivot: Pivot = Pivot.ContentCenter,
        animationSpec: AnimationSpec<TransformAnimationValues> = spring(),
        mutatePriority: MutatePriority = MutatePriority.Default
    ) = animationCoordinator.request(mutatePriority) {
        animateTo(rotationAngle, zoomScale, centerOffset, pivot, animationSpec, mutatePriority)
    }

    /**
     * Animates to center [bounds] in the content area, at the largest zoom scale that fits them at the target
     * rotation angle, with [margin] to spare. The values are clamped to their bounds. Doesn't suspend, so it can be
     * called outside a composition-bound coroutine, such as from a ViewModel. The animation starts once the layout is
     * composed and measured, replacing any earlier request of the same or lower [mutatePriority]. It is dropped if
     * an earlier request has a higher one. [snapTo], [snapBy] or [snapToBounds] before it starts, discards the
     * request. Once it starts, it cancels any ongoing fling or animation of the same or lower priority, continuing
     * the velocity of a running animation, or is skipped if one of higher priority is running.
     *
     * See [animateToBounds] to animate to the same fit in a coroutine, or [snapToBounds] to fit immediately.
     *
     * @param bounds area in layout space to fit in the content area
     * @param rotationAngle rotation angle in degrees, or null to keep the current rotation angle
     * @param margin absolute space in Dp to keep between [bounds] and each edge of the content area, independent of zoom
     * @param animationSpec the spec animating the rotation angle, the zoom scale in log space and the center of
     * the content area in layout space, as one [TransformAnimationValues]
     * @param mutatePriority the priority of this animation, which only animations and requests of the same or higher
     * priority can interrupt. [MutatePriority.PreventUserInput] also ignores gestures and keys until the animation
     * ends, instead of letting them cancel it
     */
    fun requestAnimateToBounds(
        bounds: DpRect,
        rotationAngle: Float? = null,
        margin: Dp = 0.dp,
        animationSpec: AnimationSpec<TransformAnimationValues> = spring(),
        mutatePriority: MutatePriority = MutatePriority.Default
    ) = animationCoordinator.request(mutatePriority) {
        animateToBounds(bounds, rotationAngle, margin, animationSpec, mutatePriority)
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

    private suspend fun Geometry.animate(
        pivot: Pivot,
        startAngle: Float,
        startScale: Float,
        startCenter: Offset,
        targetAngle: Float,
        targetScale: Float,
        targetCenter: Offset,
        panDelta: Offset,
        animationSpec: AnimationSpec<TransformAnimationValues>,
        mutatePriority: MutatePriority
    ) = animationCoordinator.claim(mutatePriority) {
        animatable.run {
            val viewportPivot = pivot.viewportPosition(startAngle, startScale, startCenter.toDp())
            val initialVelocity = if (isRunning) {
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
            previousViewportPivot = viewportPivot

            snapTo(
                TransformAnimationValues(
                    rotationAngle = rotationAngle,
                    logZoomScale = ln(zoomScale),
                    layoutPivot = viewportToLayout(
                        viewportPosition = viewportPivot,
                        angle = rotationAngle,
                        scale = zoomScale,
                        center = centerOffset
                    )
                )
            )

            val clampedAngle = clampAngle(targetAngle)
            val clampedScale = clampScale(clampedAngle, targetScale)
            val clampedCenter = clampCenter(
                angle = clampedAngle,
                scale = clampedScale,
                center = targetCenter.takeIf { it.isSpecified }
                    ?: centerPreservingPivot(
                        viewportPivot = viewportPivot + panDelta,
                        angle = clampedAngle,
                        scale = clampedScale,
                        fromViewportPivot = viewportPivot,
                        fromAngle = startAngle,
                        fromScale = startScale,
                        fromCenter = startCenter
                    )
            ).toDp()

            animateTo(
                targetValue = TransformAnimationValues(
                    rotationAngle = clampedAngle,
                    logZoomScale = ln(clampedScale),
                    layoutPivot = viewportToLayout(
                        viewportPosition = viewportPivot,
                        angle = clampedAngle,
                        scale = clampedScale,
                        center = clampedCenter
                    )
                ),
                animationSpec = animationSpec,
                initialVelocity = initialVelocity
            ) {
                val angle = clampAngle(value.rotationAngle)
                val scale = clampScale(angle, exp(value.logZoomScale))
                rotationAngle = angle
                zoomScale = scale
                centerOffsetPx = clampCenter(
                    angle = angle,
                    scale = scale,
                    center = centerFromLayoutPivot(
                        viewportPivot = viewportPivot,
                        angle = angle,
                        scale = scale,
                        layoutPivot = value.layoutPivot
                    )
                )
            }

            // settle any rounding errors
            clampAndSet(
                angle = clampedAngle,
                scale = clampedScale,
                center = clampedCenter
            )
        }
    }

    private val animationCoordinator = AnimationCoordinator()

    /**
     * Cancels any ongoing or requested fling or animation of the same or lower [mutatePriority].
     *
     * @param mutatePriority the priority of this cancellation. [MutatePriority.PreventUserInput] cancels any animation.
     */
    fun cancelAnimation(mutatePriority: MutatePriority = MutatePriority.Default) {
        animationCoordinator.tryCancel(mutatePriority)
    }

    internal suspend fun runPendingAnimations() = animationCoordinator.runPending()

    internal suspend fun fling(
        initialRotationVelocity: Float = 0f,
        initialLogZoomVelocity: Float = 0f,
        initialPanVelocity: Velocity = Velocity.Zero,
        viewportPivot: Offset = Offset.Unspecified,
        overscrollEffect: OverscrollEffect? = null
    ) = geometry?.run {
        if (initialRotationVelocity == 0f
            && initialLogZoomVelocity == 0f
            && initialPanVelocity.x == 0f
            && initialPanVelocity.y == 0f
            || !animationCoordinator.canCancel(MutatePriority.Default)
        ) {
            // release overscroll
            overscrollEffect?.applyToFling(Velocity.Zero) { Velocity.Zero }
        } else animationCoordinator.claim(MutatePriority.Default) {
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

    private suspend fun Geometry.flingX(xVelocity: Float, boundsChanging: () -> Boolean) = Velocity(
        x = flingPan(xVelocity, boundsChanging) { delta ->
            panBy(Offset(delta, 0f)).x
        },
        y = 0f
    )

    private suspend fun Geometry.flingY(yVelocity: Float, boundsChanging: () -> Boolean) = Velocity(
        x = 0f,
        y = flingPan(yVelocity, boundsChanging) { delta ->
            panBy(Offset(0f, delta)).y
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
            if (!applyDelta(value - previousValue)) animationCoordinator.cancel()
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
