package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.runtime.Composable

internal class LazyTransformableLayoutItemProvider(
    private val content: LazyTransformableLayoutLayerContent
) : LazyLayoutItemProvider {

    override val itemCount
        get() = content.itemCount

    @Composable
    override fun Item(index: Int, key: Any) {
        content.withInterval(index) { localIndex, layer ->
            layer.content(localIndex)
        }
    }
}
