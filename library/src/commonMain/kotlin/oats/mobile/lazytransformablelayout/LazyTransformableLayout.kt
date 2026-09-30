package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.extension.detectTransformGestures
import oats.mobile.lazytransformablelayout.extension.radians
import oats.mobile.lazytransformablelayout.extension.transform
import oats.mobile.lazytransformablelayout.extension.vertices
import oats.mobile.lazytransformablelayout.model.Positionable
import oats.mobile.lazytransformablelayout.model.SpatialBucketQuadtree
import kotlin.math.cos
import kotlin.math.sin

private const val LazyCompositionBuffer = 256f

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

    var quadtree by remember { mutableStateOf<SpatialBucketQuadtree?>(null) }
    val density = LocalDensity.current
    LaunchedEffect(content) {
        quadtree = SpatialBucketQuadtree.build(
            layoutBounds = state.layoutBounds,
            items = content.intervals,
            pxBounds = {
                with(density) { bounds.toRect() }
            }
        )
    }

    var fling: Job? by remember { mutableStateOf(null) }
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
            .pointerInput(Unit) {
                detectTransformGestures(
                    onTransformStopped = { rotationVelocity, logZoomVelocity, panVelocity, centroid ->
                        fling = scope.launch {
                            state.fling(
                                initialRotationVelocity = rotationVelocity,
                                initialLogZoomVelocity = logZoomVelocity,
                                initialPanVelocity = -panVelocity,
                                centroid = centroid,
                                overscrollEffect = overscrollEffect
                            )
                        }
                    }
                ) { rotationDelta, zoomFactor, panDelta, centroid ->
                    state.transform(
                        rotationDelta = rotationDelta,
                        zoomFactor = zoomFactor,
                        panDelta = panDelta,
                        centroid = centroid,
                        overscrollEffect = overscrollEffect
                    )
                }
            }.pointerInput(Unit) {
                detectTapGestures(
                    onPress = { fling?.cancel() }
                )
            }
    ) { constraints ->
        state.acceptConstraints(constraints)

        val (offset, angle, scale) = state

        val constraintWidth = constraints.maxWidth
        val constraintHeight = constraints.maxHeight

        val viewport = Rect(
            left = 0f,
            top = 0f,
            right = constraintWidth.toFloat(),
            bottom = constraintHeight.toFloat()
        ).inflate(LazyCompositionBuffer).run {
            val angleRadians = (-angle).radians
            val cos = cos(angleRadians)
            val sin = sin(angleRadians)

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY

            vertices.forEach {
                val p = Offset(it) + offset
                val x = (p.x * cos - p.y * sin) / scale
                val y = (p.x * sin + p.y * cos) / scale
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }

            Rect(
                left = minX,
                top = minY,
                right = maxX,
                bottom = maxY
            )
        }

        val indexedItemsToMeasure = mutableListOf<IndexedValue<Positionable>>()
        quadtree
            ?.query(viewport, indexedItemsToMeasure)
            ?: content.intervals.takeIf { it.size > 0 }?.forEach { layer ->
                layer.value.items.forEachIndexed { localIndex, positionable ->
                    if (positionable.bounds.toRect().overlaps(viewport))
                        indexedItemsToMeasure += IndexedValue(layer.startIndex + localIndex, positionable)
                }
            }

        layout(constraintWidth, constraintHeight) {
            indexedItemsToMeasure.fastForEach { (index, item) ->
                val itemBounds = item.bounds.toRect()

                val itemConstraints = itemBounds.roundToIntRect().run {
                    Constraints(
                        maxWidth = width,
                        maxHeight = height
                    )
                }

                val itemPosition = itemBounds.topLeft

                compose(index).fastForEach { measurable ->
                    val placeable = measurable.measure(itemConstraints)

                    var right = Float.NEGATIVE_INFINITY
                    var bottom = Float.NEGATIVE_INFINITY
                    itemPosition.run {
                        Rect(
                            left = x,
                            top = y,
                            right = x + placeable.width,
                            bottom = y + placeable.height
                        )
                    }.vertices.forEach {
                        Offset(it).transform(scale, angle, offset).run {
                            if (x > right) right = x
                            if (y > bottom) bottom = y
                        }
                    }

                    if (right >= -LazyCompositionBuffer
                        && bottom >= -LazyCompositionBuffer
                    ) {
                        placeable.placeWithLayer(
                            position = itemPosition.transform(scale, angle).round(),
                            zIndex = item.zIndex
                        ) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = scale
                            scaleY = scale
                            rotationZ = angle
                            translationX = -offset.x
                            translationY = -offset.y
                        }
                    }
                }
            }
        }
    }
}
