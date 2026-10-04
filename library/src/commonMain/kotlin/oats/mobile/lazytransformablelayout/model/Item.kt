package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Rect
import oats.mobile.lazytransformablelayout.extension.extent

internal data class Item(
    val index: Int,
    val bounds: Rect,
    val zIndex: Float
) {
    val extent: Float = bounds.extent
}
