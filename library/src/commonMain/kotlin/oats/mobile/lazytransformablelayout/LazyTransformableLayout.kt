package oats.mobile.lazytransformablelayout

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.extension.detectTapSequenceGestures
import oats.mobile.lazytransformablelayout.extension.detectTransformGestures
import oats.mobile.lazytransformablelayout.extension.transform
import oats.mobile.lazytransformablelayout.model.Item
import oats.mobile.lazytransformablelayout.model.Pivot
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
 * Once focused, for example by clicking, arrow keys pan and the plus and minus keys zoom.
 * All of these are ignored while an animation started with [androidx.compose.foundation.MutatePriority.PreventUserInput] runs.
 *
 * @param state The state holding this layout's rotation angle, zoom scale and center offset
 * @param modifier The modifier to apply to this layout
 * @param contentPadding Padding that insets the content area within the viewport, such as space under overlaid UI.
 * The center offset is placed at the center of the content area, and fitting and clamping use it,
 * while items still draw underneath the padding.
 * See [LazyTransformableLayoutState] for these terms.
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

    var layoutSize by remember { mutableStateOf(Size.Zero) }

    val scope = rememberCoroutineScope()

    val focusRequester = remember { FocusRequester() }

    var wheelPivot by remember { mutableStateOf(Offset.Zero) }

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
            .onSizeChanged { layoutSize = it.toSize() }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && state.isUserInputBlocked) {
                    val panStep = layoutSize.minDimension * 0.1f
                    val (zoomFactor, panDelta) = when (event.key) {
                        Key.DirectionLeft -> 1f to Offset(panStep, 0f)
                        Key.DirectionRight -> 1f to Offset(-panStep, 0f)
                        Key.DirectionUp -> 1f to Offset(0f, panStep)
                        Key.DirectionDown -> 1f to Offset(0f, -panStep)
                        Key.Plus -> TAP_ZOOM_FACTOR to Offset.Zero
                        Key.Minus -> 1 / TAP_ZOOM_FACTOR to Offset.Zero
                        else -> return@onKeyEvent false
                    }

                    scope.launch {
                        state.animateBy(
                            zoomFactor = zoomFactor,
                            panDelta = panDelta,
                            additive = true
                        )
                    }

                    true
                } else false
            }.focusRequester(focusRequester)
            .focusable()
            .scrollable(
                state = rememberScrollableState { delta ->
                    if (!state.isUserInputBlocked) state.snapBy(
                        pivot = Pivot.Viewport(wheelPivot),
                        zoomFactor = 2f.pow(delta / SCROLL_PIXELS_PER_ZOOM_DOUBLING)
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
                                viewportPivot = wheelPivot
                            )
                            return 0f
                        }
                    }
                },
                bringIntoViewSpec = object : BringIntoViewSpec {
                    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
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
                    },
                    onScroll = { wheelPivot = it }
                ) { centroid, rotationDelta, zoomFactor, panDelta ->
                    if (!state.isUserInputBlocked) state.snapBy(
                        pivot = Pivot.Viewport(centroid),
                        rotationDelta = rotationDelta,
                        zoomFactor = zoomFactor,
                        panDelta = panDelta,
                        overscrollEffect = overscrollEffect
                    )
                }
            }.pointerInput(state) {
                detectTapGestures(
                    onPress = {
                        if (!state.isUserInputBlocked) state.cancelAnimation()
                        focusRequester.requestFocus()
                    },
                    onDoubleTap = { position ->
                        if (!state.isUserInputBlocked) scope.launch {
                            state.animateBy(zoomFactor = TAP_ZOOM_FACTOR, pivot = Pivot.Viewport(position), additive = true)
                        }
                    }
                )
            }.pointerInput(state) {
                detectTapSequenceGestures(
                    onTwoFingerTap = {
                        if (!state.isUserInputBlocked) scope.launch {
                            state.animateBy(zoomFactor = 1 / TAP_ZOOM_FACTOR, additive = true)
                        }
                    },
                    onSecondaryDoubleClick = { position ->
                        if (!state.isUserInputBlocked) scope.launch {
                            state.animateBy(zoomFactor = 1 / TAP_ZOOM_FACTOR, pivot = Pivot.Viewport(position), additive = true)
                        }
                    },
                    onDoubleTapDragStopped = { logZoomVelocity ->
                        scope.launch {
                            state.fling(initialLogZoomVelocity = logZoomVelocity)
                        }
                    },
                    onDoubleTapDrag = { if (!state.isUserInputBlocked) state.snapBy(zoomFactor = it) }
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
