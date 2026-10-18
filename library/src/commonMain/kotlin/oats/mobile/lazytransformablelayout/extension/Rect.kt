package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.geometry.Rect

internal val Rect.vertices
    get() = arrayOf(topLeft, topRight, bottomRight, bottomLeft)
