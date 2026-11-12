package mobile.oats.lazytransformablelayout.extension

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect

val DpRect.center
    get() = DpOffset(
        x = (left + right) / 2f,
        y = (top + bottom) / 2f
    )
