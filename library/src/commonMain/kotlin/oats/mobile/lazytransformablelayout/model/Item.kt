package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Constraints

internal data class Item(
    val index: Int,
    val constraints: Constraints,
    val position: Offset,
    val zIndex: Float
)
