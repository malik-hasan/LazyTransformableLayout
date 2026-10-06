package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Rect

internal data class Item(
    val index: Int,
    val bounds: Rect,
    val zIndex: Float
) {
    val maxDimension: Float = bounds.maxDimension
}
