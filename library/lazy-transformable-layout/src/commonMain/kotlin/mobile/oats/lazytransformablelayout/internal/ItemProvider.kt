package mobile.oats.lazytransformablelayout.internal

import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import mobile.oats.lazytransformablelayout.LazyTransformableLayoutState
import mobile.oats.lazytransformablelayout.LocalLazyTransformableLayoutState

internal class ItemProvider(
    private val content: Content,
    private val state: LazyTransformableLayoutState
) : LazyLayoutItemProvider {

    override val itemCount
        get() = content.itemCount

    @Composable
    override fun Item(index: Int, key: Any) = key(key) {
        content.withInterval(index) { localIndex, layer ->
            val density = LocalDensity.current
            val downscale = density.downscaleOf(layer.items[localIndex].bounds)
            CompositionLocalProvider(
                LocalLazyTransformableLayoutState provides state,
                LocalDensity provides if (downscale == 1) density
                else Density(density.density / downscale, density.fontScale)
            ) {
                layer.content(localIndex)
            }
        }
    }

    override fun getKey(index: Int) = content.getKey(index)

    override fun getContentType(index: Int) = content.getContentType(index)
}
