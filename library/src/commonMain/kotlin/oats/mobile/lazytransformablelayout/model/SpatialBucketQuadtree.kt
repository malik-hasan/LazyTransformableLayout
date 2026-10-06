package oats.mobile.lazytransformablelayout.model

import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class SpatialBucketQuadtree private constructor(
    private val nodeBounds: Array<Rect>,
    private val nodeItemStart: IntArray,
    private val nodeItemCount: IntArray,
    private val nodeItems: Array<Item>,
    private val nodeLargestItemDimension: FloatArray,
    private val nodeChildren: IntArray // 4 per node
) {
    fun query(viewportBounds: Parallelogram, minItemDimension: Float, out: MutableList<Item>) {
        if (nodeBounds.isNotEmpty()) queryNode(0, viewportBounds, minItemDimension, out)
    }

    private fun queryNode(
        nodeIndex: Int,
        viewportBounds: Parallelogram,
        minItemDimension: Float,
        out: MutableList<Item>
    ) {
        if (nodeIndex == -1
            || nodeLargestItemDimension[nodeIndex] < minItemDimension
            || !viewportBounds.intersects(nodeBounds[nodeIndex])
        ) return

        val nodeItemStartIndex = nodeItemStart[nodeIndex]
        nodeItems.sliceArray(nodeItemStartIndex until nodeItemStartIndex + nodeItemCount[nodeIndex])
            .forEach { item ->
                if (item.maxDimension >= minItemDimension && viewportBounds.intersects(item.bounds))
                    out += item
            }

        val nodeChildrenStartIndex = nodeIndex * 4
        nodeChildren.sliceArray(nodeChildrenStartIndex until nodeChildrenStartIndex + 4)
            .forEach { child ->
                queryNode(child, viewportBounds, minItemDimension, out)
            }
    }

    companion object {
        private class Node(
            val bounds: Rect,
            val items: List<Item>,
            val children: Array<Node?>?
        ) {
            val largestItemDimension: Float = run {
                var max = 0f
                items.fastForEach {
                    max = maxOf(max, it.maxDimension)
                }
                children?.forEach { child ->
                    child?.let {
                        max = maxOf(max, it.largestItemDimension)
                    }
                }
                max
            }

            companion object {
                fun build(
                    bounds: Rect,
                    items: List<Item>,
                    depth: Int
                ): Node {
                    if (items.size <= 32 || depth >= 12) return Node(bounds, items, children = null)

                    val midX = bounds.center.x
                    val midY = bounds.center.y
                    val childBounds = bounds.run {
                        arrayOf(
                            Rect(left, top, midX, midY),
                            Rect(midX, top, right, midY),
                            Rect(left, midY, midX, bottom),
                            Rect(midX, midY, right, bottom)
                        )
                    }

                    val buckets = Array(4) { mutableListOf<Item>() }

                    val straddling = mutableListOf<Item>()
                    items.forEach { item ->
                        val itemBounds = item.bounds
                        val left = itemBounds.right <= midX
                        val right = itemBounds.left >= midX
                        val top = itemBounds.bottom <= midY
                        val bottom = itemBounds.top >= midY
                        when {
                            left && top -> 0
                            right && top -> 1
                            left && bottom -> 2
                            right && bottom -> 3
                            else -> null
                        }?.let { quadrant ->
                            buckets[quadrant] += item
                        } ?: straddling.add(item)
                    }

                    return Node(
                        bounds = bounds,
                        items = straddling,
                        children = (0 until 4).map { i ->
                            if (buckets[i].isEmpty()) {
                                null
                            } else build(childBounds[i], buckets[i], depth + 1)
                        }.toTypedArray()
                    )
                }
            }
        }

        suspend fun build(
            layoutBounds: Rect,
            intervals: IntervalList<LazyTransformableLayoutLayer>,
            pxBounds: Positionable.() -> Rect
        ) = withContext(Dispatchers.Default) {
            if (intervals.size == 0) return@withContext null

            val positionables = mutableListOf<Positionable>()
            val positionableBounds = mutableListOf<Rect>()
            var left = layoutBounds.left
            var top = layoutBounds.top
            var right = layoutBounds.right
            var bottom = layoutBounds.bottom
            intervals.forEach { layer ->
                layer.value.items.forEach { positionable ->
                    ensureActive()
                    val pxBounds = positionable.pxBounds()
                    positionables += positionable
                    positionableBounds += pxBounds
                    left = minOf(left, pxBounds.left)
                    top = minOf(top, pxBounds.top)
                    right = maxOf(right, pxBounds.right)
                    bottom = maxOf(bottom, pxBounds.bottom)
                }
            }

            val drawOrder = FloatArray(positionables.size)
            positionables.indices
                .sortedBy { positionables[it].zIndex }
                .fastForEachIndexed { rank, i ->
                    drawOrder[i] = rank.toFloat()
                }

            val allItems = List(positionables.size) { i ->
                Item(
                    index = i,
                    bounds = positionableBounds[i],
                    zIndex = drawOrder[i]
                )
            }

            val root = Node.build(
                bounds = Rect(left, top, right, bottom),
                items = allItems,
                depth = 0
            )

            val nodeBounds = mutableListOf<Rect>()
            val nodeItemStart = mutableListOf<Int>()
            val nodeItemCount = mutableListOf<Int>()
            val nodeItems = mutableListOf<Item>()
            val nodeLargestItemDimension = mutableListOf<Float>()
            val nodeChildren = mutableListOf<Int>()

            fun Node.visit(): Int {
                ensureActive()
                val index = nodeBounds.size
                nodeBounds += bounds
                nodeItemStart += nodeItems.size
                nodeItemCount += items.size
                nodeItems += items
                nodeLargestItemDimension += largestItemDimension
                repeat(4) { nodeChildren += -1 }
                children?.let {
                    val nodeChildrenStartIndex = index * 4
                    for (i in 0 until 4) {
                        nodeChildren[nodeChildrenStartIndex + i] = children[i]?.visit() ?: -1
                    }
                }
                return index
            }
            root.visit()

            SpatialBucketQuadtree(
                nodeBounds = nodeBounds.toTypedArray(),
                nodeItemStart = nodeItemStart.toIntArray(),
                nodeItemCount = nodeItemCount.toIntArray(),
                nodeItems = nodeItems.toTypedArray(),
                nodeLargestItemDimension = nodeLargestItemDimension.toFloatArray(),
                nodeChildren = nodeChildren.toIntArray()
            )
        }
    }
}
