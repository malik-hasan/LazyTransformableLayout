package mobile.oats.lazytransformablelayout.tiledimage.internal

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntRect
import mobile.oats.lazytransformablelayout.Positionable

internal class Tile(
    val key: String,
    val level: Int,
    val region: IntRect,
    override val bounds: DpRect,
    override val zIndex: Float
) : Positionable
