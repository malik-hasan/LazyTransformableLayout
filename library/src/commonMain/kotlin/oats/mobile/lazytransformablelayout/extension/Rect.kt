package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal val Rect.vertices
    get() = arrayOf(topLeft, topRight, bottomRight, bottomLeft)

internal fun Rect.vertex(index: Int): Offset = when (index) {
    0 -> topLeft
    1 -> topRight
    2 -> bottomRight
    else -> bottomLeft
}
