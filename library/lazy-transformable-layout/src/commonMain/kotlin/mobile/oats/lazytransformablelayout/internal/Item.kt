package mobile.oats.lazytransformablelayout.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

internal class Item(
    val index: Int,
    val constraints: Constraints,
    val position: Offset,
    val size: Size,
    val zIndex: Float
)

internal fun itemConstraints(width: Float, height: Float) = Constraints.fitPrioritizingWidth(
    minWidth = 0,
    maxWidth = width.roundToInt(),
    minHeight = 0,
    maxHeight = height.roundToInt()
)
