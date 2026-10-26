package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset

context(density: Density)
fun DpOffset.toPx() = with(density) {
    Offset(x.toPx(), y.toPx())
}
