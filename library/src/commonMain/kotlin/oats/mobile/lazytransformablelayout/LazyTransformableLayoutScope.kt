package oats.mobile.lazytransformablelayout

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import oats.mobile.lazytransformablelayout.model.Positionable

interface LazyTransformableLayoutScope {

    fun item(
        offset: DpOffset,
        size: DpSize,
        zIndex: Float = 0f,
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable (Positionable) -> Unit
    ) = item(
        bounds = DpRect(offset, size),
        zIndex = zIndex,
        key = key,
        contentType = contentType,
        content = content
    )

    fun item(
        bounds: DpRect,
        zIndex: Float = 0f,
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable (Positionable) -> Unit
    ) = item(
        item = object : Positionable {
            override val bounds = bounds
            override val zIndex = zIndex
        },
        key = key,
        contentType = contentType,
        content = content
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
    ) = itemsIndexed(
        items = items,
        key = key?.let {
            { _, item -> key(item) }
        },
        contentType = { _, item -> contentType(item) }
    ) { _, item -> content(item) }

    fun <T : Positionable> itemsIndexed(
        items: List<T>,
        key: ((Int, T) -> Any)? = null,
        contentType: (Int, T) -> Any? = { _, _ -> null },
        content: @Composable (Int, T) -> Unit
    )
}
