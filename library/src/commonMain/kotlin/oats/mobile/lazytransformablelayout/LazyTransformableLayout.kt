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
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.extension.detectTapSequenceGestures
import oats.mobile.lazytransformablelayout.extension.detectTransformGestures
import oats.mobile.lazytransformablelayout.extension.detectWheelZoomGestures
import oats.mobile.lazytransformablelayout.extension.transform
import oats.mobile.lazytransformablelayout.internal.Content
import oats.mobile.lazytransformablelayout.internal.Item
import oats.mobile.lazytransformablelayout.internal.ItemProvider
import kotlin.math.roundToInt

private const val TAP_ZOOM_FACTOR = 2f

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
                if (event.type == KeyEventType.KeyDown) {
                    val panStep = with(density) { 100.dp.toPx() }
                    val (zoomFactor, panDelta) = when (event.key) {
                        Key.DirectionLeft -> 1f to Offset(panStep, 0f)
                        Key.DirectionRight -> 1f to Offset(-panStep, 0f)
                        Key.DirectionUp -> 1f to Offset(0f, panStep)
                        Key.DirectionDown -> 1f to Offset(0f, -panStep)
                        Key.Plus, Key.Equals, Key.NumPadAdd, Key.PageUp -> TAP_ZOOM_FACTOR to Offset.Zero
                        Key.Minus, Key.NumPadSubtract, Key.PageDown -> 1 / TAP_ZOOM_FACTOR to Offset.Zero
                        else -> return@onKeyEvent false
                    }

                    scope.launch {
                        state.animateBy(zoomFactor = zoomFactor, panDelta = panDelta)
                    }

                    true
                } else false
            }.focusRequester(focusRequester)
            .focusable()
            .pointerInput(state) {
                detectWheelZoomGestures { position, zoomFactor ->
                    scope.launch {
                        state.animateBy(
                            pivot = Pivot.Viewport(position),
                            zoomFactor = zoomFactor,
                            mutatePriority = MutatePriority.UserInput
                        )
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
                    },
                    onDoubleTap = { position ->
                        scope.launch {
                            state.animateBy(
                                pivot = Pivot.Viewport(position),
                                zoomFactor = TAP_ZOOM_FACTOR
                            )
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
