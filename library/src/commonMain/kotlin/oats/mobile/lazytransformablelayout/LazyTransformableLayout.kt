package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.extension.detectTapSequenceGestures
import oats.mobile.lazytransformablelayout.extension.detectTransformGestures
import oats.mobile.lazytransformablelayout.extension.transform
import oats.mobile.lazytransformablelayout.model.Item
import oats.mobile.lazytransformablelayout.model.Pivot
import oats.mobile.lazytransformablelayout.model.WheelPivot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private const val TAP_ZOOM_FACTOR = 2f
private const val SCROLL_PIXELS_PER_ZOOM_DOUBLING = 545f

/**
 * A lazy layout of [oats.mobile.lazytransformablelayout.model.Positionable] items that can be panned, zoomed and rotated.
 * Only items intersecting the viewport are composed.
 * Double tap zooms in, two-finger tap zooms out, and double tap then drag zooms continuously.
 * With a mouse, dragging pans, shift-dragging rotates, the wheel zooms at the cursor, and a secondary double click
 * zooms out. Trackpad pinch and two-finger swipes zoom and pan where the platform reports them.
 * Once focused, for example by clicking, arrow keys pan, and the plus, equals, numpad plus and Page Up keys zoom in
 * while the minus, numpad minus and Page Down keys zoom out.
 * Dragging, pinching, the wheel and pressing mutate with [MutatePriority.UserInput], so they interrupt animations of
 * [MutatePriority.Default] priority, while taps, keys and flings start animations of [MutatePriority.Default] priority.
 * All of these are ignored while an animation started with [MutatePriority.PreventUserInput] runs or is requested.
 *
 * @param state The state holding this layout's rotation angle, zoom scale and center offset
 * @param modifier The modifier to apply to this layout
 * @param contentPadding Padding that insets the content area within the viewport, such as space under overlaid UI.
 * The center offset is placed at the center of the content area, and fitting and clamping use it,
 * while items still draw underneath the padding.
 * @param overscrollEffect The effect shown when panning past the layout bounds
 * @param contentBuilder Declares the items of this layout. It runs again whenever its lambda instance changes
 * or state it reads changes. Keep it stable: avoid capturing values that change often, and read frequently
 * changing state inside the item content or the [oats.mobile.lazytransformablelayout.model.Positionable] fields instead.
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

    val density = LocalDensity.current

    val scope = rememberCoroutineScope()

    val focusRequester = remember { FocusRequester() }

    val wheelPivot = remember { WheelPivot() }

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
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val panStep = with(density) { 100.dp.toPx() }
                    val (zoomFactor, panDelta) = when (event.key) {
                        Key.DirectionLeft -> 1f to Offset(panStep, 0f)
                        Key.DirectionRight -> 1f to Offset(-panStep, 0f)
                        Key.DirectionUp -> 1f to Offset(0f, panStep)
                        Key.DirectionDown -> 1f to Offset(0f, -panStep)
                        Key.Plus, Key.Equals, Key.NumPadAdd, Key.PageUp -> TAP_ZOOM_FACTOR to Offset.Zero
                        Key.Minus, Key.NumPadSubtract, Key.PageDown -> 1 / TAP_ZOOM_FACTOR to Offset.Zero
                        else -> return@onPreviewKeyEvent false
                    }

                    scope.launch {
                        state.animateBy(zoomFactor = zoomFactor, panDelta = panDelta)
                    }

                    true
                } else false
            }.focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Scroll) {
                            wheelPivot.position = event.changes.first().position
                        }
                    }
                }
            }.scrollable(
                state = rememberScrollableState { delta ->
                    state.snapBy(
                        viewportPivot = wheelPivot.position,
                        zoomFactor = 2f.pow(delta / SCROLL_PIXELS_PER_ZOOM_DOUBLING),
                        mutatePriority = MutatePriority.UserInput
                    )
                    delta
                },
                orientation = Orientation.Vertical,
                overscrollEffect = null,
                flingBehavior = remember(state) {
                    object : FlingBehavior {
                        override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                            state.fling(
                                initialLogZoomVelocity = initialVelocity * ln(2f) / SCROLL_PIXELS_PER_ZOOM_DOUBLING,
                                viewportPivot = wheelPivot.position
                            )
                            return 0f
                        }
                    }
                },
                bringIntoViewSpec = remember {
                    object : BringIntoViewSpec {
                        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
                    }
                }
            ).pointerInput(state, overscrollEffect) {
                detectTransformGestures(
                    onTransformStopped = { centroid, rotationVelocity, logZoomVelocity, panVelocity ->
                        scope.launch {
                            state.fling(
                                viewportPivot = centroid,
                                initialRotationVelocity = rotationVelocity,
                                initialLogZoomVelocity = logZoomVelocity,
                                initialPanVelocity = -panVelocity,
                                overscrollEffect = overscrollEffect
                            )
                        }
                    }
                ) { centroid, rotationDelta, zoomFactor, panDelta ->
                    state.snapBy(
                        viewportPivot = centroid,
                        rotationDelta = rotationDelta,
                        zoomFactor = zoomFactor,
                        panDelta = panDelta,
                        overscrollEffect = overscrollEffect,
                        mutatePriority = MutatePriority.UserInput
                    )
                }
            }.pointerInput(state) {
                detectTapGestures(
                    onPress = {
                        state.cancelAnimation(MutatePriority.UserInput)
                        focusRequester.requestFocus()
                    },
                    onDoubleTap = { position ->
                        scope.launch {
                            state.animateBy(Pivot.Viewport(position), zoomFactor = TAP_ZOOM_FACTOR)
                        }
                    }
                )
            }.pointerInput(state) {
                detectTapSequenceGestures(
                    onTwoFingerTap = {
                        scope.launch {
                            state.animateBy(zoomFactor = 1 / TAP_ZOOM_FACTOR)
                        }
                    },
                    onSecondaryDoubleClick = { position ->
                        scope.launch {
                            state.animateBy(Pivot.Viewport(position), zoomFactor = 1 / TAP_ZOOM_FACTOR)
                        }
                    },
                    onDoubleTapDragStopped = { logZoomVelocity ->
                        scope.launch {
                            state.fling(initialLogZoomVelocity = logZoomVelocity)
                        }
                    },
                    onDoubleTapDrag = {
                        state.snapBy(zoomFactor = it, mutatePriority = MutatePriority.UserInput)
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
