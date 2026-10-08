package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent
import androidx.compose.foundation.lazy.layout.MutableIntervalList
import androidx.compose.runtime.Composable
import oats.mobile.lazytransformablelayout.model.LazyTransformableLayoutLayer
import oats.mobile.lazytransformablelayout.model.Positionable

internal class Content(
    buildContent: LazyTransformableLayoutScope.() -> Unit
) : LazyLayoutIntervalContent<LazyTransformableLayoutLayer>(), LazyTransformableLayoutScope {

    override val intervals: IntervalList<LazyTransformableLayoutLayer>
        field = MutableIntervalList()

    override fun <T : Positionable> item(
        item: T,
        key: Any?,
        contentType: Any?,
        content: @Composable (T) -> Unit
    ) = intervals.addInterval(
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

    override fun <T : Positionable> itemsIndexed(
        items: List<T>,
        key: ((Int, T) -> Any)?,
        contentType: (Int, T) -> Any?,
        content: @Composable (Int, T) -> Unit
    ) = intervals.addInterval(
        size = items.size,
        value = LazyTransformableLayoutLayer(
            items = items,
            key = key?.let {
                { i -> key(i, items[i]) }
            },
            type = { i -> contentType(i, items[i]) }
        ) { i -> content(i, items[i]) }
    )

    init { buildContent() }
}
