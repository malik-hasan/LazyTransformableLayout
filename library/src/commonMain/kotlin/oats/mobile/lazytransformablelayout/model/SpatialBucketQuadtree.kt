package oats.mobile.lazytransformablelayout.model

import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class SpatialBucketQuadtree private constructor(
    private val nodeBounds: Array<Rect>,
    private val childIndices: IntArray, // 4 slots per node, -1 = no child
    private val itemStart: IntArray,
    private val itemNodeCount: IntArray,
    private val itemIndices: Array<IndexedValue<Positionable>>
) {

    context(density: Density)
    fun query(rect: Rect, out: MutableList<IndexedValue<Positionable>>) {
        if (nodeBounds.isNotEmpty()) queryNode(0, rect, out)
    }

    context(density: Density)
    private fun queryNode(nodeIndex: Int, rect: Rect, out: MutableList<IndexedValue<Positionable>>) {
        if (nodeIndex == -1 || !nodeBounds[nodeIndex].overlaps(rect)) return

        val start = itemStart[nodeIndex]
        val end = start + itemNodeCount[nodeIndex]

        for (i in start until end) {
            val item = itemIndices[i]
            with(density) {
                if (item.value.bounds.toRect().overlaps(rect)) {
                    out += item
                }
            }
        }

        val base = nodeIndex * 4
        for (c in 0 until 4) queryNode(childIndices[base + c], rect, out)
    }

    companion object {
        private class Node(
            val bounds: Rect,
            val items: List<IndexedValue<Positionable>>,
            val children: Array<Node?>?
        ) {
            companion object {
                fun build(
                    bounds: Rect,
                    items: List<Pair<IndexedValue<Positionable>, Rect>>,
                    depth: Int
                ): Node {
                    if (items.size <= 8 || depth >= 16)
                        return Node(bounds, items.fastMap { it.first }, children = null)

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

                    val buckets = Array(4) { mutableListOf<Pair<IndexedValue<Positionable>, Rect>>() }
                    val straddling = mutableListOf<IndexedValue<Positionable>>()
                    items.fastForEach { pair ->
                        val (item, itemBounds) = pair

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
                            buckets[quadrant] += pair
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
            pxBounds: Positionable.() -> Rect,
            items: IntervalList<LazyTransformableLayoutLayer>
        ) = withContext(Dispatchers.Default) {
            if (items.size == 0) return@withContext null

            val withBounds = mutableListOf<Pair<IndexedValue<Positionable>, Rect>>()
            var left = layoutBounds.left
            var top = layoutBounds.top
            var right = layoutBounds.right
            var bottom = layoutBounds.bottom
            items.forEach { layer ->
                layer.value.items.forEachIndexed { localIndex, positionable ->
                    ensureActive()
                    val pxBounds = positionable.pxBounds()
                    withBounds += IndexedValue(layer.startIndex + localIndex, positionable) to pxBounds
                    left = minOf(left, pxBounds.left)
                    top = minOf(top, pxBounds.top)
                    right = maxOf(right, pxBounds.right)
                    bottom = maxOf(bottom, pxBounds.bottom)
                }
            }

            val root = Node.build(
                bounds = Rect(left, top, right, bottom),
                items = withBounds,
                depth = 0
            )

            val nodeBoundsList = mutableListOf<Rect>()
            val childIndicesList = mutableListOf<Int>()
            val itemStartList = mutableListOf<Int>()
            val itemNodeCountList = mutableListOf<Int>()
            val itemIndicesList = mutableListOf<IndexedValue<Positionable>>()

            fun Node.visit(): Int {
                ensureActive()
                val myIndex = nodeBoundsList.size
                nodeBoundsList += bounds
                itemStartList += itemIndicesList.size
                itemNodeCountList += this.items.size
                itemIndicesList += this.items
                val base = myIndex * 4
                repeat(4) { childIndicesList += -1 }
                children?.forEachIndexed { q, child ->
                    child?.let { childIndicesList[base + q] = it.visit() }
                }
                return myIndex
            }
            root.visit()

            SpatialBucketQuadtree(
                nodeBounds = nodeBoundsList.toTypedArray(),
                childIndices = childIndicesList.toIntArray(),
                itemStart = itemStartList.toIntArray(),
                itemNodeCount = itemNodeCountList.toIntArray(),
                itemIndices = itemIndicesList.toTypedArray()
            )
        }
    }
}
