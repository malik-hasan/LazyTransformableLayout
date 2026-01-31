package oats.mobile.lazypannablelayout

import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.runtime.Composable

internal class LazyPannableLayoutItemProvider(
    private val content: LazyPannableLayoutLayerContent
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
