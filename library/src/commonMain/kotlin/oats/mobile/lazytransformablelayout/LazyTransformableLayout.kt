package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.round
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.model.Positionable
import oats.mobile.lazytransformablelayout.utility.detectTransformGestures
import oats.mobile.lazytransformablelayout.utility.transform
import oats.mobile.lazytransformablelayout.utility.vertices

@Composable
fun LazyTransformableLayout(
    state: LazyTransformableLayoutState,
    modifier: Modifier = Modifier,
    contentBuilder: LazyTransformableLayoutScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val overscrollEffect = rememberOverscrollEffect()

    val latestContentBuilder by rememberUpdatedState(contentBuilder)

    val layerContent by remember {
        derivedStateOf(referentialEqualityPolicy()) {
            LazyTransformableLayoutLayerContent(latestContentBuilder)
        }
    }

    var fling: Job? by remember { mutableStateOf(null) }

    LazyLayout(
        itemProvider = remember {
            derivedStateOf(referentialEqualityPolicy()) {
                LazyTransformableLayoutItemProvider(layerContent)
            }::value
        },
        modifier = modifier
            .clipToBounds()
            .overscroll(overscrollEffect)
            .pointerInput(Unit) {
                detectTransformGestures(
                    onTransformStopped = { logZoomVelocity, rotationVelocity, negativeVelocity ->
                        val panVelocity = -negativeVelocity
                        fling = scope.launch {
                            launch {
                                overscrollEffect?.applyToFling(panVelocity.copy(y = 0f)) { velocity ->
                                    state.flingX(velocity)
                                } ?: state.flingX(panVelocity)
                            }
                            launch {
                                overscrollEffect?.applyToFling(panVelocity.copy(x = 0f)) { velocity ->
                                    state.flingY(velocity)
                                } ?: state.flingY(panVelocity)
                            }
                        }
                    }
                ) { zoomFactor, rotationDelta, panDelta, centroid ->
                    val scaledPanDelta = panDelta / zoomFactor
                    overscrollEffect?.applyToScroll(scaledPanDelta, NestedScrollSource.UserInput) { panDelta ->
                        state.transform(
                            zoomFactor = zoomFactor,
                            rotationDelta = rotationDelta,
                            panDelta = panDelta,
                            centroid = centroid
                        )
                    } ?: state.transform(
                        zoomFactor = zoomFactor,
                        rotationDelta = rotationDelta,
                        panDelta = scaledPanDelta,
                        centroid = centroid
                    )
                }
            }.pointerInput(Unit) {
                detectTapGestures(
                    onPress = { fling?.cancel() }
                )
            }
    ) { constraints ->
        state.acceptConstraints(constraints)

        val scale = state.scale
        val angle = state.angle
        val offset = state.offset

        val constraintWidth = constraints.maxWidth
        val constraintHeight = constraints.maxHeight
        val buffer = 256f

        val indexedItemsToMeasure = mutableListOf<IndexedValue<Positionable>>()
        layerContent.intervals.forEach { layer ->
            layer.value.items.forEachIndexed { localIndex, item ->
                var left = Float.MAX_VALUE
                var top = Float.MAX_VALUE
                var right = Float.NEGATIVE_INFINITY
                var bottom = Float.NEGATIVE_INFINITY
                item.bounds.toRect().vertices.forEach {
                    Offset(it).transform(scale, angle, offset).run {
                        if (x < left) left = x
                        if (y < top) top = y
                        if (x > right) right = x
                        if (y > bottom) bottom = y
                    }
                }

                if (left <= constraintWidth + buffer
                    && top <= constraintHeight + buffer
                    && right >= -buffer
                    && bottom >= -buffer
                ) indexedItemsToMeasure += IndexedValue(layer.startIndex + localIndex, item)
            }
        }

        val offsetX = offset.x
        val offsetY = offset.y

        layout(constraintWidth, constraintHeight) {
            indexedItemsToMeasure.fastForEach { (index, item) ->
                compose(index).fastForEach { measurable ->
                    val placeable = measurable.measure(constraints)

                    var left = Float.MAX_VALUE
                    var top = Float.MAX_VALUE
                    val itemBounds = item.bounds.toRect()
                    itemBounds.vertices.forEach {
                        Offset(it).transform(scale, angle, offset).run {
                            if (x < left) left = x
                            if (y < top) top = y
                        }
                    }

                    if (left + placeable.width >= -buffer
                        && top + placeable.height >= -buffer
                    ) {
                        placeable.placeWithLayer(
                            position = itemBounds.topLeft.transform(scale, angle).round(),
                            zIndex = item.zIndex
                        ) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = scale
                            scaleY = scale
                            rotationZ = angle
                            translationX = -offsetX
                            translationY = -offsetY
                        }
                    }
                }
            }
        }
    }
}
