package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.runtime.Composable

internal class ItemProvider(
    private val content: Content
) : LazyLayoutItemProvider {

    override val itemCount
        get() = content.itemCount

    @Composable
    override fun Item(index: Int, key: Any) {
        content.withInterval(index) { localIndex, layer ->
            layer.content(localIndex)
        }
    }

    override fun getKey(index: Int) = content.getKey(index)

    override fun getContentType(index: Int) = content.getContentType(index)
}
