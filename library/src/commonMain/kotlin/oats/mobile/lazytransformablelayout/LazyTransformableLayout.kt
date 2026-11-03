package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.extension.detectTapSequenceGestures
import oats.mobile.lazytransformablelayout.extension.detectTransformGestures
import oats.mobile.lazytransformablelayout.extension.detectWheelZoomGestures
import oats.mobile.lazytransformablelayout.extension.trackpadPinchZoom
import oats.mobile.lazytransformablelayout.extension.transform
import oats.mobile.lazytransformablelayout.internal.Content
import oats.mobile.lazytransformablelayout.internal.Item
import oats.mobile.lazytransformablelayout.internal.ItemProvider
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val TAP_ZOOM_FACTOR = 2f

private const val WHEEL_ZOOM_TIME_CONSTANT_NANOS = 40_000_000f

private const val MIN_WHEEL_LOG_ZOOM_STEP = 0.001f

private val arrowKeys = setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)

private val zoomInKeys = setOf(Key.Plus, Key.Equals, Key.NumPadAdd, Key.PageUp)

private val zoomOutKeys = setOf(Key.Minus, Key.NumPadSubtract, Key.PageDown)

/**
 * A lazy layout of [Positionable] items that can be panned, zoomed and rotated.
 * Only items intersecting the viewport are composed, with some buffer.
 *
 * @param state The state holding this layout's rotation angle, zoom scale and center offset
 * @param modifier The modifier to apply to this layout
 * @param contentPadding Padding that insets the content area within the viewport
 * @param overscrollEffect The effect shown when panning past the layout bounds
 * @param contentBuilder Declares the items of this layout. It runs again whenever its lambda instance changes
 * or state it reads changes. Keep it stable: avoid capturing values that change often, and read frequently
 * changing state inside the item content or the [Positionable] fields instead.
 */
@Composable
fun LazyTransformableLayout(
    state: LazyTransformableLayoutState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    contentBuilder: LazyTransformableLayoutScope.() -> Unit
) {
    val latestContentBuilder by rememberUpdatedState(contentBuilder)
    val content by remember {
        derivedStateOf(referentialEqualityPolicy()) {
            Content(latestContentBuilder)
        }
    }

    val heldKeys = remember { mutableSetOf<Key>() }
    var keyJob by remember { mutableStateOf<Job?>(null) }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state) { state.runPendingAnimations() }

    LazyLayout(
        itemProvider = remember {
            derivedStateOf(referentialEqualityPolicy()) {
                ItemProvider(content)
            }::value
        },
        modifier = modifier
            .clipToBounds()
            .overscroll(overscrollEffect)
            .onKeyEvent { event ->
                when(event.type) {
                    KeyEventType.KeyUp -> heldKeys.run {
                        remove(event.key).also {
                            if (isEmpty()) {
                                keyJob?.cancel()
                                keyJob = null
                            }
                        }
                    }

                    KeyEventType.KeyDown -> {
                        val key = event.key
                        if (key in arrowKeys || key in zoomInKeys || key in zoomOutKeys) {
                            if (heldKeys.add(event.key)) {
                                keyJob?.cancel()
                                keyJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    val panStep = with(density) { 100.dp.toPx() }
                                    while (true) {
                                        var zoomFactor = 1f
                                        if (heldKeys.any { it in zoomInKeys }) zoomFactor *= TAP_ZOOM_FACTOR
                                        if (heldKeys.any { it in zoomOutKeys }) zoomFactor /= TAP_ZOOM_FACTOR

                                        var x = 0f
                                        var y = 0f
                                        if (Key.DirectionLeft in heldKeys) x += panStep
                                        if (Key.DirectionRight in heldKeys) x -= panStep
                                        if (Key.DirectionUp in heldKeys) y += panStep
                                        if (Key.DirectionDown in heldKeys) y -= panStep

                                        scope.launch {
                                            state.animateBy(
                                                zoomFactor = zoomFactor,
                                                panDelta = Offset(x, y)
                                            )
                                        }
                                        delay(50.milliseconds)
                                    }
                                }
                            }
                            true
                        } else false
                    }

                    else -> false
                }
            }.focusRequester(focusRequester)
            .focusable()
            .trackpadPinchZoom(
                onZoomStopped = { position, logZoomVelocity ->
                    scope.launch {
                        state.fling(
                            pivot = Pivot.Viewport(position),
                            initialLogZoomVelocity = logZoomVelocity
                        )
                    }
                }
            ) { position, zoomFactor ->
                state.snapBy(
                    pivot = Pivot.Viewport(position),
                    zoomFactor = zoomFactor,
                    mutatePriority = MutatePriority.UserInput
                )
            }.pointerInput(state) {
                var pendingLogZoom = 0f
                var wheelJob: Job? = null
                detectWheelZoomGestures { position, zoomFactor ->
                    pendingLogZoom += ln(zoomFactor)
                    if (wheelJob?.isActive != true) wheelJob = scope.launch {
                        var previousFrameNanos = withFrameNanos { it }
                        val pivot = Pivot.Viewport(position)
                        while (pendingLogZoom != 0f) {
                            val frameNanos = withFrameNanos { it }
                            val logZoomStep = if (abs(pendingLogZoom) < MIN_WHEEL_LOG_ZOOM_STEP) {
                                pendingLogZoom
                            } else pendingLogZoom * (
                                1 - exp((previousFrameNanos - frameNanos) / WHEEL_ZOOM_TIME_CONSTANT_NANOS)
                            )
                            previousFrameNanos = frameNanos
                            pendingLogZoom -= logZoomStep
                            state.snapBy(
                                pivot = pivot,
                                zoomFactor = exp(logZoomStep),
                                mutatePriority = MutatePriority.UserInput
                            )
                        }
                    }
                }
            }.pointerInput(state, overscrollEffect) {
                detectTransformGestures(
                    onTransform = { centroid, rotationDelta, zoomFactor, panDelta ->
                        state.snapBy(
                            pivot = Pivot.Viewport(centroid),
                            rotationDelta = rotationDelta,
                            zoomFactor = zoomFactor,
                            panDelta = panDelta,
                            overscrollEffect = overscrollEffect,
                            mutatePriority = MutatePriority.UserInput
                        )
                    },
                    onTransformStopped = { centroid, rotationVelocity, logZoomVelocity, panVelocity ->
                        scope.launch {
                            state.fling(
                                pivot = Pivot.Viewport(centroid),
                                initialRotationVelocity = rotationVelocity,
                                initialLogZoomVelocity = logZoomVelocity,
                                initialPanVelocity = -panVelocity,
                                overscrollEffect = overscrollEffect
                            )
                        }
                    }
                )
            }.pointerInput(state) {
                detectTapGestures(
                    onPress = {
                        state.cancelAnimation(MutatePriority.UserInput)
                        focusRequester.requestFocus()
                    }
                )
            }.pointerInput(state) {
                var tapZoomScale = 1f
                fun zoomByTap(pivot: Pivot, zoomFactor: Float, repeated: Boolean) {
                    tapZoomScale = (if (repeated) tapZoomScale else state.zoomScale) * zoomFactor
                    scope.launch {
                        state.animateTo(zoomScale = tapZoomScale, pivot = pivot)
                    }
                }

                detectTapSequenceGestures(
                    onDoubleTap = { position ->
                        zoomByTap(Pivot.Viewport(position), TAP_ZOOM_FACTOR, false)
                    },
                    onTwoFingerTap = { repeated ->
                        zoomByTap(Pivot.ContentCenter, 1 / TAP_ZOOM_FACTOR, repeated)
                    },
                    onSecondaryDoubleClick = { position ->
                        scope.launch {
                            state.animateBy(
                                pivot = Pivot.Viewport(position),
                                zoomFactor = 1 / TAP_ZOOM_FACTOR
                            )
                        }
                    },
                    onDoubleTapDrag = { zoomFactor ->
                        state.snapBy(
                            zoomFactor = zoomFactor,
                            mutatePriority = MutatePriority.UserInput
                        )
                    },
                    onDoubleTapDragStopped = { logZoomVelocity ->
                        scope.launch {
                            state.fling(initialLogZoomVelocity = logZoomVelocity)
                        }
                    }
                )
            }
    ) { constraints ->
        state.measure(constraints, contentPadding)
        val (minItemDimension, viewportBounds) = state.compositionBounds!!

        val itemsToCompose = mutableListOf<Item>()
        content.intervals.takeIf { it.size > 0 }?.forEach { interval ->
            for (localIndex in 0 until interval.size) {
                val positionable = interval.value.items[localIndex]
                val bounds = positionable.bounds
                val left = bounds.left.toPx()
                val top = bounds.top.toPx()
                val right = bounds.right.toPx()
                val bottom = bounds.bottom.toPx()
                val width = right - left
                val height = bottom - top

                if (maxOf(width, height) >= minItemDimension
                    && viewportBounds.intersects(left, top, right, bottom)
                ) itemsToCompose += Item(
                    index = interval.startIndex + localIndex,
                    constraints = Constraints(
                        maxWidth = width.roundToInt(),
                        maxHeight = height.roundToInt()
                    ),
                    position = Offset(left, top),
                    zIndex = positionable.zIndex
                )
            }
        }

        val placeables = itemsToCompose.fastMap { item ->
            compose(item.index).fastMap {
                it.measure(item.constraints)
            }
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            itemsToCompose.fastForEachIndexed { i, item ->
                val itemPosition = item.position

                placeables[i].fastForEach { placeable ->
                    if (itemPosition.run {
                        viewportBounds.intersects(
                            left = x,
                            top = y,
                            right = x + placeable.width,
                            bottom = y + placeable.height
                        )
                    }) placeable.placeWithLayer(
                        position = IntOffset.Zero,
                        zIndex = item.zIndex
                    ) {
                        transformOrigin = TransformOrigin(0f, 0f)

                        val scale = state.zoomScale
                        scaleX = scale
                        scaleY = scale

                        val angle = state.rotationAngle
                        rotationZ = angle

                        val translation = itemPosition.transform(scale, angle, state.translation)
                        translationX = translation.x
                        translationY = translation.y
                    }
                }
            }
        }
    }
}
