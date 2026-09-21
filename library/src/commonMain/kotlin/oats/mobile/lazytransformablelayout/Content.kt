package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent
import androidx.compose.foundation.lazy.layout.MutableIntervalList
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpRect
import oats.mobile.lazytransformablelayout.model.LazyTransformableLayoutLayer
import oats.mobile.lazytransformablelayout.model.Positionable

internal class Content(
    buildContent: LazyTransformableLayoutScope.() -> Unit
) : LazyLayoutIntervalContent<LazyTransformableLayoutLayer>(), LazyTransformableLayoutScope {

    override val intervals: IntervalList<LazyTransformableLayoutLayer>
        field = MutableIntervalList()

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
    ) = intervals.addInterval(
        size = 1,
        value = LazyTransformableLayoutLayer(
            items = listOf(item),
            key = key?.let { { key } },
            type = { contentType }
        ) {
            content(item)
        }
    )

    override fun <T : Positionable> items(
        items: List<T>,
        key: ((T) -> Any)?,
        contentType: (T) -> Any?,
        content: @Composable (T) -> Unit
    ) = itemsIndexed(
        items = items,
        key = key?.let { { _, item -> key(item) } },
        contentType = { _, item -> contentType(item) }
    ) { _, item -> content(item) }

    override fun <T : Positionable> itemsIndexed(
        items: List<T>,
        key: ((Int, T) -> Any)?,
        contentType: (Int, T) -> Any?,
        content: @Composable (Int, T) -> Unit
    ) = intervals.addInterval(
        size = items.size,
        value = LazyTransformableLayoutLayer(
            items = items,
            key = key?.let { { i -> key(i, items[i]) } },
            type = { i -> contentType(i, items[i]) }
        ) { i -> content(i, items[i]) }
    )

    init {
        buildContent()
        val seen = HashSet<Any>(itemCount)
        for (i in 0 until itemCount) {
            require(seen.add(getKey(i))) {
                "Duplicate key ${getKey(i)} at index $i in LazyTransformableLayout content"
            }
        }
    }
}
