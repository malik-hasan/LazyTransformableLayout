package mobile.oats.lazytransformablelayout.internal

import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent
import androidx.compose.foundation.lazy.layout.MutableIntervalList
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import mobile.oats.lazytransformablelayout.LazyTransformableLayoutScope
import mobile.oats.lazytransformablelayout.Positionable

internal class Content(
    buildContent: LazyTransformableLayoutScope.() -> Unit
) : LazyLayoutIntervalContent<Layer>(), LazyTransformableLayoutScope {

    override val intervals: IntervalList<Layer>
        field = MutableIntervalList()

    override fun <T : Positionable> item(
        item: T,
        key: Any?,
        contentType: Any?,
        content: @Composable (T) -> Unit
    ) {
        intervals.addInterval(
            size = 1,
            value = Layer(
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

    override fun <T : Positionable> itemsIndexed(
        items: List<T>,
        key: ((Int, T) -> Any)?,
        contentType: (Int, T) -> Any?,
        content: @Composable (Int, T) -> Unit
    ) {
        intervals.addInterval(
            size = items.size,
            value = Layer(
                items = items,
                key = key?.let {
                    { i -> key(i, items[i]) }
                },
                type = { i -> contentType(i, items[i]) }
            ) { i -> content(i, items[i]) }
        )
    }

    init { buildContent() }

    context(density: Density)
    inline fun forEachItem(
        crossinline block: (
            index: Int,
            positionable: Positionable,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float
        ) -> Unit
    ) = intervals.takeIf { it.size > 0 }?.forEach { interval ->
        for (localIndex in 0 until interval.size) {
            val positionable = interval.value.items[localIndex]
            with(density) {
                positionable.bounds.run {
                    block(
                        interval.startIndex + localIndex,
                        positionable,
                        left.toPx(),
                        top.toPx(),
                        right.toPx(),
                        bottom.toPx()
                    )
                }
            }
        }
    }
}
