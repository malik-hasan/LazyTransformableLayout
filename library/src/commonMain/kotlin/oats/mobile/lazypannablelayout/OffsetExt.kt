package oats.mobile.lazypannablelayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset

context(density: Density)
fun Offset.toDpOffset() = with(density) {
    DpOffset(x.toDp(), y.toDp())
}
