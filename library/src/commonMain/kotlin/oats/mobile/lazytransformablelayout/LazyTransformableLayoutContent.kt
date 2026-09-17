package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.util.fastForEach
import oats.mobile.lazytransformablelayout.model.Positionable

internal class LazyTransformableLayoutContent(
    density: Density,
    layoutBounds: Rect,
    buildContent: LazyTransformableLayoutScope.() -> Unit,
) : LazyLayoutItemProvider, LazyTransformableLayoutScope {

    private val itemList = mutableListOf<@Composable () -> Unit>()
    private val positionableQuadtree = BucketQuadtreeNode(layoutBounds, density)

    override val itemCount
        get() = itemList.size

    @Composable
    override fun Item(index: Int, key: Any) = itemList[index]()

    fun query(viewport: Rect, out: MutableList<IndexedValue<Positionable>>) = positionableQuadtree.query(viewport, out)

    override fun item(bounds: DpRect, zIndex: Float, content: @Composable (Positionable) -> Unit) =
        item(
            item = object : Positionable {
                override val bounds = bounds
                override val zIndex = zIndex
            },
            content = content
        )

    override fun <T : Positionable> item(item: T, content: @Composable (T) -> Unit) {
        positionableQuadtree.insert(IndexedValue(itemList.size, item))
        itemList += { content(item) }
    }

    override fun <T : Positionable> items(items: List<T>, content: @Composable (T) -> Unit) =
        items.forEach { item ->
            item(item, content)
        }

    override fun <T : Positionable> itemsIndexed(items: List<T>, content: @Composable (Int, T) -> Unit) {
        items.forEachIndexed { i, item ->
            item(item) {
                content(i, item)
            }
        }
    }

    init { buildContent() }

    private class BucketQuadtreeNode(private val bounds: Rect, private val density: Density) {
        private var items = mutableListOf<IndexedValue<Positionable>>()
        private var children: Array<BucketQuadtreeNode>? = null

        fun insert(item: IndexedValue<Positionable>) {
            children?.let { quadrants ->
                quadrantFor(bounds, item)?.let { quadrant ->
                    quadrants[quadrant].insert(item)
                    return
                }
                items += item
                return
            }

            items += item
            if (items.size > 8) {
                split()
            }
        }

        private fun split() {
            val midX = bounds.center.x
            val midY = bounds.center.y
            val quadrants = Array(4) { i ->
                BucketQuadtreeNode(
                    when (i) {
                        0 -> Rect(bounds.left, bounds.top, midX, midY)
                        1 -> Rect(midX, bounds.top, bounds.right, midY)
                        2 -> Rect(bounds.left, midY, midX, bounds.bottom)
                        else -> Rect(midX, midY, bounds.right, bounds.bottom)
                    },
                    density
                )
            }
            children = quadrants

            val remaining = mutableListOf<IndexedValue<Positionable>>()
            items.fastForEach { item ->
                quadrantFor(bounds, item)?.let { quadrant ->
                    quadrants[quadrant].items += item
                } ?: remaining.add(item)
            }
            items = remaining
        }

        fun query(viewport: Rect, out: MutableList<IndexedValue<Positionable>>) {
            if (bounds.overlaps(viewport)) {
                out += items
                children?.forEach {
                    it.query(viewport, out)
                }
            }
        }

        private fun quadrantFor(nodeBounds: Rect, item: IndexedValue<Positionable>): Int? {
            val itemBounds = with(density) {
                item.value.bounds.toRect()
            }

            val midX = nodeBounds.center.x
            val midY = nodeBounds.center.y
            val left = itemBounds.right <= midX
            val right = itemBounds.left >= midX
            val top = itemBounds.bottom <= midY
            val bottom = itemBounds.top >= midY
            return when {
                left && top -> 0
                right && top -> 1
                left && bottom -> 2
                right && bottom -> 3
                else -> null
            }
        }
    }
}
