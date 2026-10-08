package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import oats.mobile.lazytransformablelayout.model.Item
import oats.mobile.lazytransformablelayout.model.LazyTransformableLayoutLayer
import oats.mobile.lazytransformablelayout.model.Parallelogram
import kotlin.math.roundToInt

internal class SpatialIndex(
    intervals: IntervalList<LazyTransformableLayoutLayer>,
    density: Density
) {
    private val bounds = FloatArray(intervals.size * 4)
    private val zIndices = FloatArray(intervals.size)

    init {
        intervals.takeIf { it.size > 0 }?.forEach { layer ->
            for (localIndex in 0 until layer.size) {
                val positionable = layer.value.items[localIndex]
                val i = layer.startIndex + localIndex
                val itemBounds = positionable.bounds
                with(density) {
                    itemBounds.run {
                        val boundsStartIndex = i * 4
                        bounds[boundsStartIndex] = left.toPx()
                        bounds[boundsStartIndex + 1] = top.toPx()
                        bounds[boundsStartIndex + 2] = right.toPx()
                        bounds[boundsStartIndex + 3] = bottom.toPx()
                    }
                }
                zIndices[i] = positionable.zIndex
            }
        }
    }

    fun query(minItemDimension: Float, viewportBounds: Parallelogram, out: MutableList<Item>) {
        val viewportAABB = viewportBounds.axisAlignedBoundingBox

        for (i in zIndices.indices) {
            val boundsStartIndex = i * 4
            val left = bounds[boundsStartIndex]
            val top = bounds[boundsStartIndex + 1]
            val right = bounds[boundsStartIndex + 2]
            val bottom = bounds[boundsStartIndex + 3]
            if (right >= viewportAABB.left
                && left <= viewportAABB.right
                && bottom >= viewportAABB.top
                && top <= viewportAABB.bottom
                && maxOf(right - left, bottom - top) > minItemDimension
                && viewportBounds.intersects(left, top, right, bottom)
            ) out += Item(
                index = i,
                constraints = Constraints(
                    maxWidth = (right - left).roundToInt(),
                    maxHeight = (bottom - top).roundToInt()
                ),
                position = Offset(left, top),
                zIndex = zIndices[i]
            )
        }
    }
}
