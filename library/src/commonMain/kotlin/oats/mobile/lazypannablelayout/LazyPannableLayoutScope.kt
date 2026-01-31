package oats.mobile.lazypannablelayout

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpRect
import oats.mobile.lazypannablelayout.model.Positionable

interface LazyPannableLayoutScope {
    fun item(bounds: DpRect, z: Float = 0f, content: @Composable (Positionable) -> Unit)
    fun <T : Positionable> item(item: T, content: @Composable (T) -> Unit)
    fun <T : Positionable> items(items: List<T>, content: @Composable (T) -> Unit)
    fun <T : Positionable> itemsIndexed(items: List<T>, content: @Composable (Int, T) -> Unit)
}
