package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect

internal val Rect.vertices
    get() = arrayOf(topLeft, topRight, bottomRight, bottomLeft)

internal val DpRect.center
    get() = DpOffset((left + right) / 2, (top + bottom) / 2)
