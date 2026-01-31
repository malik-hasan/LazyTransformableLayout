package oats.mobile.lazypannablelayout

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import oats.mobile.lazypannablelayout.model.Positionable
import oats.mobile.lazypannablelayout.utility.rotateBy
import oats.mobile.lazypannablelayout.utility.times
import oats.mobile.lazypannablelayout.utility.toDpOffset

@Composable
fun LazyPannableLayout(
    state: LazyPannableLayoutState,
    modifier: Modifier = Modifier,
    contentBuilder: LazyPannableLayoutScope.() -> Unit
) {
    val overscrollEffect = rememberOverscrollEffect()

    val latestContentBuilder by rememberUpdatedState(contentBuilder)

    val layerContent by remember {
        derivedStateOf(referentialEqualityPolicy()) {
            LazyPannableLayoutLayerContent(latestContentBuilder)
        }
    }

    var fling: Job? by remember { mutableStateOf(null) }

    LazyLayout(
        itemProvider = remember {
            derivedStateOf(referentialEqualityPolicy()) {
                LazyPannableLayoutItemProvider(layerContent)
            }::value
        },
        modifier = modifier
            .clipToBounds()
            .overscroll(overscrollEffect)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panDelta, zoomFactor, rotationDelta ->//( TODO
//                    onTransformStopped = { logZoomVelocity, rotationVelocity, panVelocity ->
//                        Log.d("MALIK", "$logZoomVelocity $rotationVelocity $panVelocity")
//                        fling = scope.launch {
//                            launch {
//                                overscrollEffect?.applyToFling(panVelocity.copy(y = 0f)) { velocity ->
//                                    state.flingX(velocity)
//                                } ?: state.flingX(panVelocity)
//                            }
//                            launch {
//                                overscrollEffect?.applyToFling(panVelocity.copy(x = 0f)) { velocity ->
//                                    state.flingY(velocity)
//                                } ?: state.flingY(panVelocity)
//                            }
//                        }
//                    }
                //) { zoomFactor, rotationDelta, panDelta, centroid ->
                    val scaledPanDelta = panDelta / zoomFactor
                    val dpCentroid = centroid.toDpOffset()
                    // TODO
//                    overscrollEffect?.applyToScroll(scaledPanDelta, NestedScrollSource.UserInput) { panDelta ->
//                        state.transform(
//                            zoomFactor = zoomFactor,
//                            rotationDelta = rotationDelta,
//                            panDelta = panDelta.toDpOffset(),
//                            centroid = dpCentroid
//                        ).toOffset()
//                    } ?:
                    state.transform(
                        zoomFactor = zoomFactor,
                        rotationDelta = rotationDelta,
                        panDelta = scaledPanDelta.toDpOffset(),
                        centroid = dpCentroid
                    )
                }
            }.pointerInput(Unit) {
                detectTapGestures(
                    onPress = { fling?.cancel() }
                )
            }
    ) { constraints ->
        val offset = state.offset
        val offsetX = offset.x
        val offsetY = offset.y

        val constraintWidth = constraints.maxWidth
        val constraintHeight = constraints.maxHeight
        state.passConstraints(constraints)

        val scale = state.scale

        val indexedItemsToMeasure = mutableListOf<IndexedValue<Positionable>>()
        layerContent.intervals.forEach { layer ->
            layer.value.items.forEachIndexed { localIndex, item ->
                val itemBounds = item.bounds
                if (true || itemBounds.right + offsetX >= 0.dp // TODO
                    && itemBounds.bottom + offsetY >= 0.dp
                    && itemBounds.left + offsetX <= (constraintWidth / scale).toDp()
                    && itemBounds.top + offsetY <= (constraintHeight / scale).toDp()
                ) indexedItemsToMeasure += IndexedValue(layer.startIndex + localIndex, item)
            }
        }

        layout(constraintWidth, constraintHeight) {
            indexedItemsToMeasure.forEach { (index, item) ->
                compose(index).forEach { measurable ->
                    val placeable = measurable.measure(constraints)

                    val itemBounds = item.bounds
                    // TODO: Check the actual width/height again after measurement
//                    if (offsetLeftBound + placeable.width.toDp() >= 0.dp
//                        && offsetTopBound + placeable.height.toDp() >= 0.dp

                    val rotation = state.angle

                    val itemPosition = (
                        DpOffset(
                        itemBounds.left,
                        itemBounds.top
                        ) * scale
                    ).rotateBy(rotation)

                    placeable.placeWithLayer(
                        x = itemPosition.x.roundToPx(),
                        y = itemPosition.y.roundToPx(),
                        zIndex = item.z
                    ) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = offsetX.toPx()
                        translationY = offsetY.toPx()
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation
                    }
                }
            }
        }
    }
}
