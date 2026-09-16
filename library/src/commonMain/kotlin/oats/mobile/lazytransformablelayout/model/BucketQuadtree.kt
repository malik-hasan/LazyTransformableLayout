package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Rect

internal class BucketQuadtree private constructor(
    private val nodeBounds: Array<Rect>,
    private val childIndices: IntArray, // 4 slots per node, -1 = no child
    private val itemStart: IntArray,
    private val itemCount: IntArray,
    private val itemIndices: Array<IndexedValue<Positionable>>
) {
    fun query(rect: Rect): List<IndexedValue<Positionable>> {
        // Return early if there are no items
        if (nodeBounds.isEmpty()) return emptyList()

        val out = mutableListOf<IndexedValue<Positionable>>()
        queryNode(0, rect, out)
        return out
    }

    private fun queryNode(nodeIndex: Int, rect: Rect, out: MutableList<IndexedValue<Positionable>>) {
        if (nodeIndex == -1 || !nodeBounds[nodeIndex].overlaps(rect)) return

        val start = itemStart[nodeIndex]
        for (i in start until start + itemCount[nodeIndex]) {
            out += itemIndices[i]
        }

        val base = nodeIndex * 4
        for (c in 0 until 4) {
            queryNode(childIndices[base + c], rect, out)
        }
    }

    private class BuildNode(val bounds: Rect) {
        var children: Array<BuildNode>? = null
        val items = mutableListOf<IndexedValue<Positionable>>()
    }

    companion object {
        fun build(
            items: List<IndexedValue<Positionable>>,
            rootBounds: Rect,
            boundsOf: (Positionable) -> Rect,
            maxDepth: Int = 10,
            maxItemsPerNode: Int = 8
        ): BucketQuadtree {
            val root = BuildNode(rootBounds)
            items.forEach { indexed ->
                insert(root, indexed, boundsOf, depth = 0, maxDepth, maxItemsPerNode)
            }

            val nodeBoundsList = ArrayList<Rect>()
            val childIndicesList = ArrayList<Int>()
            val itemStartList = ArrayList<Int>()
            val itemCountList = ArrayList<Int>()
            val itemIndicesList = ArrayList<IndexedValue<Positionable>>()

            fun visit(node: BuildNode): Int {
                val myIndex = nodeBoundsList.size
                nodeBoundsList += node.bounds
                itemStartList += itemIndicesList.size
                itemCountList += node.items.size
                itemIndicesList += node.items
                val base = myIndex * 4
                repeat(4) { childIndicesList += -1 }
                node.children?.forEachIndexed { q, child ->
                    childIndicesList[base + q] = visit(child)
                }
                return myIndex
            }
            visit(root)

            return BucketQuadtree(
                nodeBoundsList.toTypedArray(),
                childIndicesList.toIntArray(),
                itemStartList.toIntArray(),
                itemCountList.toIntArray(),
                itemIndicesList.toTypedArray()
            )
        }

        private fun insert(
            node: BuildNode,
            indexed: IndexedValue<Positionable>,
            boundsOf: (Positionable) -> Rect,
            depth: Int,
            maxDepth: Int,
            maxItemsPerNode: Int
        ) {
            val children = node.children
            if (children != null) {
                val q = quadrantFor(node.bounds, boundsOf(indexed.value))
                if (q != -1) {
                    insert(children[q], indexed, boundsOf, depth + 1, maxDepth, maxItemsPerNode)
                    return
                }
                node.items += indexed
                return
            }

            node.items += indexed
            if (node.items.size > maxItemsPerNode && depth < maxDepth) {
                split(node, boundsOf)
            }
        }

        private fun quadrantFor(nodeBounds: Rect, itemBounds: Rect): Int {
            val midX = (nodeBounds.left + nodeBounds.right) / 2f
            val midY = (nodeBounds.top + nodeBounds.bottom) / 2f
            val left = itemBounds.right <= midX
            val right = itemBounds.left >= midX
            val top = itemBounds.bottom <= midY
            val bottom = itemBounds.top >= midY
            return when {
                left && top -> 0
                right && top -> 1
                left && bottom -> 2
                right && bottom -> 3
                else -> -1 // straddles the split — stays at this node
            }
        }

        private fun split(node: BuildNode, boundsOf: (Positionable) -> Rect) {
            val midX = (node.bounds.left + node.bounds.right) / 2f
            val midY = (node.bounds.top + node.bounds.bottom) / 2f
            val childBounds = arrayOf(
                Rect(node.bounds.left, node.bounds.top, midX, midY),
                Rect(midX, node.bounds.top, node.bounds.right, midY),
                Rect(node.bounds.left, midY, midX, node.bounds.bottom),
                Rect(midX, midY, node.bounds.right, node.bounds.bottom)
            )
            val children = Array(4) { BuildNode(childBounds[it]) }
            node.children = children

            val remaining = ArrayList<IndexedValue<Positionable>>()
            for (indexed in node.items) {
                val q = quadrantFor(node.bounds, boundsOf(indexed.value))
                if (q != -1) children[q].items += indexed else remaining += indexed
            }
            node.items.clear()
            node.items += remaining
        }
    }
}
