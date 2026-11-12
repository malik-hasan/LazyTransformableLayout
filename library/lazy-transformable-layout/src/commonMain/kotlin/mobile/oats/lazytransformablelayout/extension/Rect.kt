package mobile.oats.lazytransformablelayout.extension

import androidx.compose.ui.geometry.Rect

val Rect.vertices
    get() = arrayOf(topLeft, topRight, bottomRight, bottomLeft)
