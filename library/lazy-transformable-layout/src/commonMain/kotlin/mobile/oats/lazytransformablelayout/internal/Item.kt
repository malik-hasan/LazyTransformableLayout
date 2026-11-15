package mobile.oats.lazytransformablelayout.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Constraints

internal class Item(
    val index: Int,
    val constraints: Constraints,
    val position: Offset,
    val size: Size,
    val zIndex: Float
)
