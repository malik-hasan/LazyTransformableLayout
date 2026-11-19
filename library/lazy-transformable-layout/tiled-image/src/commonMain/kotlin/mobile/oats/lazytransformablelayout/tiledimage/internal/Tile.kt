package mobile.oats.lazytransformablelayout.tiledimage.internal

import androidx.compose.ui.unit.IntRect

internal data class Tile(
    val level: Int,
    val column: Int,
    val row: Int,
    val core: IntRect,
    val decoded: IntRect
)
