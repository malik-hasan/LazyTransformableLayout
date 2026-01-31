package oats.mobile.lazypannablelayout

import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent
import androidx.compose.foundation.lazy.layout.MutableIntervalList
import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.ui.unit.DpRect
import oats.mobile.lazypannablelayout.model.Positionable

internal class LazyPannableLayoutLayerContent(
    buildContent: LazyPannableLayoutScope.() -> Unit
): LazyLayoutIntervalContent<LazyPannableLayoutLayer>(), LazyPannableLayoutScope {

    val _layers = MutableIntervalList<LazyPannableLayoutLayer>()
    override val intervals: IntervalList<LazyPannableLayoutLayer> = _layers

    override fun item(bounds: DpRect, z: Float, content: @Composable (Positionable) -> Unit) =
        item(
            item = object : Positionable {
                override val bounds = bounds
                override val z = z
            },
            content = content
        )

    override fun <T : Positionable> item(item: T, content: @Composable (T) -> Unit) =
        _layers.addInterval(
            size = 1,
            value = LazyPannableLayoutLayer(listOf(item)) {
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
            value = LazyPannableLayoutLayer(items) { i ->
                content(i, items[i])
            }
        )

    init { buildContent() }
}
