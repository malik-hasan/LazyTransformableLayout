package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent
import androidx.compose.foundation.lazy.layout.MutableIntervalList
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.util.fastForEach
import oats.mobile.lazytransformablelayout.model.LazyTransformableLayoutLayer
import oats.mobile.lazytransformablelayout.model.Positionable

internal class Content(
    density: Density,
    layoutBounds: Rect,
    buildContent: LazyTransformableLayoutScope.() -> Unit,
) : LazyLayoutIntervalContent<LazyTransformableLayoutLayer>(), LazyTransformableLayoutScope {

    private val layers = MutableIntervalList<LazyTransformableLayoutLayer>()
    override val intervals: IntervalList<LazyTransformableLayoutLayer> = layers
    private val positionableQuadtree = BucketQuadtreeNode(layoutBounds, density)

    fun query(viewport: Rect, out: MutableList<IndexedValue<Positionable>>) = positionableQuadtree.query(viewport, out)

    override fun item(
        bounds: DpRect,
        zIndex: Float,
        key: Any?,
        contentType: Any?,
        content: @Composable (Positionable) -> Unit
    ) = item(
        item = object : Positionable {
            override val bounds = bounds
            override val zIndex = zIndex
        },
        key = key,
        contentType = contentType,
        content = content
    )

    override fun <T : Positionable> item(
        item: T,
        key: Any?,
        contentType: Any?,
        content: @Composable (T) -> Unit
    ) {
        positionableQuadtree.insert(IndexedValue(itemCount, item))
        layers.addInterval(
            size = 1,
            value = LazyTransformableLayoutLayer(
                items = listOf(item),
                key = key?.let {
                    { key }
                },
                type = { contentType }
            ) {
                content(item)
            }
        )
    }

    override fun <T : Positionable> items(
        items: List<T>,
        key: ((T) -> Any)?,
        contentType: (T) -> Any?,
        content: @Composable (T) -> Unit
    ) = itemsIndexed(
        items = items,
        key = key?.let {
            { _, item -> key(item) }
        },
        contentType = { _, item -> contentType(item) }
    ) { _, item -> content(item) }

    override fun <T : Positionable> itemsIndexed(
        items: List<T>,
        key: ((Int, T) -> Any)?,
        contentType: (Int, T) -> Any?,
        content: @Composable (Int, T) -> Unit
    ) {
        var index = itemCount
        items.forEach { item ->
            positionableQuadtree.insert(IndexedValue(index, item))
            index++
        }
        layers.addInterval(
            size = items.size,
            value = LazyTransformableLayoutLayer(
                items = items,
                key = key?.let {
                    { i -> key(i, items[i]) }
                },
                type = { i -> contentType(i, items[i]) }
            ) { i -> content(i, items[i]) }
        )
    }

    init {
        buildContent()
        val seen = HashSet<Any>(itemCount)
        for (i in 0 until itemCount) {
            require(seen.add(getKey(i))) {
                "Duplicate key ${getKey(i)} at index $i in LazyTransformableLayout content"
            }
        }
    }

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
