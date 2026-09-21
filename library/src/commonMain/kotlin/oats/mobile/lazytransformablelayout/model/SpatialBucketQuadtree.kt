package oats.mobile.lazytransformablelayout.model

import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SpatialBucketQuadtree private constructor(
    private val nodeBounds: Array<Rect>,
    private val childIndices: IntArray, // 4 slots per node, -1 = no child
    private val itemStart: IntArray,
    private val itemNodeCount: IntArray,
    private val itemIndices: Array<IndexedValue<Positionable>>,
    private val overflowItems: Array<IndexedValue<Positionable>>
) {

    fun query(rect: Rect, out: MutableList<IndexedValue<Positionable>>) {
        for (item in overflowItems) out += item
        if (nodeBounds.isNotEmpty()) queryNode(0, rect, out)
    }

    private fun queryNode(nodeIndex: Int, rect: Rect, out: MutableList<IndexedValue<Positionable>>) {
        if (nodeIndex == -1 || !nodeBounds[nodeIndex].overlaps(rect)) return

        val start = itemStart[nodeIndex]
        for (i in start until start + itemNodeCount[nodeIndex]) out += itemIndices[i]

        val base = nodeIndex * 4
        for (c in 0 until 4) queryNode(childIndices[base + c], rect, out)
    }

    companion object {
        private class Node(
            val bounds: Rect,
            val items: List<IndexedValue<Positionable>>,
            val children: Array<Node>?
        ) {
            companion object {
                fun build(
                    bounds: Rect,
                    items: List<Pair<IndexedValue<Positionable>, Rect>>,
                    depth: Int
                ): Node {
                    if (items.size <= 8)
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
                            build(childBounds[i], buckets[i], depth + 1)
                        }.toTypedArray()
                    )
                }
            }
        }

        suspend fun build(
            items: IntervalList<LazyTransformableLayoutLayer>,
            pxBounds: Positionable.() -> Rect,
            layoutBounds: Rect,
        ) = withContext(Dispatchers.Default) {
            if (items.size == 0) return@withContext null

            val withBounds = mutableMapOf<Positionable, Rect>()
            val inBounds = mutableListOf<Pair<IndexedValue<Positionable>, Rect>>()
            val overflow = mutableListOf<IndexedValue<Positionable>>()
            items.forEach { layer ->
                layer.value.items.forEachIndexed { localIndex, positionable ->
                    val pxBounds = positionable.pxBounds()
                    withBounds[positionable] = pxBounds

                    val indexedPositionable = IndexedValue(layer.startIndex + localIndex, positionable)
                    if (layoutBounds.left <= pxBounds.left
                        && layoutBounds.top <= pxBounds.top
                        && pxBounds.right <= layoutBounds.right
                        && pxBounds.bottom <= layoutBounds.bottom
                    ) {
                        inBounds += indexedPositionable to pxBounds
                    } else overflow += indexedPositionable
                }
            }

            val root = Node.build(layoutBounds, inBounds, depth = 0)

            val nodeBoundsList = mutableListOf<Rect>()
            val childIndicesList = mutableListOf<Int>()
            val itemStartList = mutableListOf<Int>()
            val itemNodeCountList = mutableListOf<Int>()
            val itemIndicesList = mutableListOf<IndexedValue<Positionable>>()

            fun Node.visit(): Int {
                val myIndex = nodeBoundsList.size
                nodeBoundsList += bounds
                itemStartList += itemIndicesList.size
                itemNodeCountList += this.items.size
                itemIndicesList += this.items
                val base = myIndex * 4
                repeat(4) { childIndicesList += -1 }
                children?.forEachIndexed { q, child ->
                    childIndicesList[base + q] = child.visit()
                }
                return myIndex
            }
            root.visit()

            SpatialBucketQuadtree(
                nodeBounds = nodeBoundsList.toTypedArray(),
                childIndices = childIndicesList.toIntArray(),
                itemStart = itemStartList.toIntArray(),
                itemNodeCount = itemNodeCountList.toIntArray(),
                itemIndices = itemIndicesList.toTypedArray(),
                overflowItems = overflow.toTypedArray()
            )
        }
    }
}
