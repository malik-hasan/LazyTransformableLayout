package oats.mobile.lazytransformablelayout

import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent
import androidx.compose.foundation.lazy.layout.MutableIntervalList
import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.ui.unit.DpRect
import oats.mobile.lazytransformablelayout.model.LazyTransformableLayoutLayer
import oats.mobile.lazytransformablelayout.model.Positionable

internal class LazyTransformableLayoutLayerContent(
    buildContent: LazyTransformableLayoutScope.() -> Unit
): LazyLayoutIntervalContent<LazyTransformableLayoutLayer>(), LazyTransformableLayoutScope {

    val _layers = MutableIntervalList<LazyTransformableLayoutLayer>()
    override val intervals: IntervalList<LazyTransformableLayoutLayer> = _layers

    override fun item(bounds: DpRect, zIndex: Float, content: @Composable (Positionable) -> Unit) =
        item(
            item = object : Positionable {
                override val bounds = bounds
                override val zIndex = zIndex
            },
            content = content
        )

    override fun <T : Positionable> item(item: T, content: @Composable (T) -> Unit) =
        _layers.addInterval(
            size = 1,
            value = LazyTransformableLayoutLayer(listOf(item)) {
                content(item)
            }
        )

    override fun <T : Positionable> items(items: List<T>, content: @Composable (T) -> Unit) =
        itemsIndexed(items) { _, item ->
            content(item)
        }

    override fun <T : Positionable> itemsIndexed(items: List<T>, content: @Composable (Int, T) -> Unit) =
        _layers.addInterval(
            size = items.size,
            value = LazyTransformableLayoutLayer(items) { i ->
                content(i, items[i])
            }
        )

    init { buildContent() }
}
