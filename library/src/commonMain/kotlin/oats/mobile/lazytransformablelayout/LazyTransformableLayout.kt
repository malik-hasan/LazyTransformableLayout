package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.extension.detectTransformGestures
import oats.mobile.lazytransformablelayout.extension.transform
import oats.mobile.lazytransformablelayout.model.Item

/**
 * A lazy layout of [oats.mobile.lazytransformablelayout.model.Positionable] items that can be panned, zoomed and rotated.
 * Only items intersecting the viewport are composed.
 *
 * @param state The camera state of this layout
 * @param modifier The modifier to apply to this layout
 * @param overscrollEffect The effect shown when panning past the layout bounds
 * @param contentBuilder Declares the items of this layout. It runs again whenever its lambda instance changes
 * or state it reads changes, and every run re-reads the bounds of all items and remeasures the layout.
 * Keep it stable: avoid capturing values that change often, and read frequently changing state
 * inside the item content instead.
 */
@Composable
fun LazyTransformableLayout(
    state: LazyTransformableLayoutState,
    modifier: Modifier = Modifier,
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
    val spatialIndex by remember(density.density) {
        derivedStateOf(referentialEqualityPolicy()) {
            SpatialIndex(content.intervals, density)
        }
    }

    val scope = rememberCoroutineScope()

    LazyLayout(
        itemProvider = remember {
            derivedStateOf(referentialEqualityPolicy()) {
                ItemProvider(content)
            }::value
        },
        modifier = modifier
            .clipToBounds()
            .overscroll(overscrollEffect)
            .pointerInput(state, overscrollEffect) {
                detectTransformGestures(
                    onTransformStopped = { centroid, rotationVelocity, logZoomVelocity, panVelocity ->
                         scope.launch {
                            state.fling(
                                centroid = centroid,
                                initialRotationVelocity = rotationVelocity,
                                initialLogZoomVelocity = logZoomVelocity,
                                initialPanVelocity = -panVelocity,
                                overscrollEffect = overscrollEffect
                            )
                        }
                    }
                ) { centroid, rotationDelta, zoomFactor, panDelta ->
                    state.transform(
                        centroid = centroid,
                        rotationDelta = rotationDelta,
                        zoomFactor = zoomFactor,
                        panDelta = panDelta,
                        overscrollEffect = overscrollEffect
                    )
                }
            }.pointerInput(state) {
                detectTapGestures(
                    onPress = { state.cancelFling() }
                )
            }
    ) { constraints ->
        state.acceptConstraints(constraints)

        val (minItemDimension, viewportBounds) = state.compositionBounds
            ?: return@LazyLayout layout(constraints.maxWidth, constraints.maxHeight) {}

        val items = mutableListOf<Item>()
        spatialIndex.query(minItemDimension, viewportBounds, items)
        val placeables = items.fastMap { item ->
            compose(item.index).fastMap {
                it.measure(item.constraints)
            }
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            items.fastForEachIndexed { i, item ->
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
                        val (offset, angle, scale) = state
                        scaleX = scale
                        scaleY = scale
                        rotationZ = angle
                        val translation = itemPosition.transform(scale, angle, offset)
                        translationX = translation.x
                        translationY = translation.y
                    }
                }
            }
        }
    }
}
