package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width

val DpRect.center
    get() = DpOffset(width / 2f, height / 2f)
