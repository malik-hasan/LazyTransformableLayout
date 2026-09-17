package oats.mobile.lazytransformablelayout

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpRect
import oats.mobile.lazytransformablelayout.model.Positionable

interface LazyTransformableLayoutScope {
    fun item(
        bounds: DpRect,
        zIndex: Float,
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable (Positionable) -> Unit
    )

    fun <T : Positionable> item(
        item: T,
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable (T) -> Unit
    )

    fun <T : Positionable> items(
        items: List<T>,
        key: ((T) -> Any)? = null,
        contentType: (T) -> Any? = { null },
        content: @Composable (T) -> Unit
    )

    fun <T : Positionable> itemsIndexed(
        items: List<T>,
        key: ((Int, T) -> Any)? = null,
        contentType: (Int, T) -> Any? = { _, _ -> null },
        content: @Composable (Int, T) -> Unit
    )
}
