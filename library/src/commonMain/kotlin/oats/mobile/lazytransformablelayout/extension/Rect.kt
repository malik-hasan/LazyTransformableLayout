package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.geometry.Rect

internal val Rect.vertices
    get() = longArrayOf(
        topLeft.packedValue,
        topRight.packedValue,
        bottomRight.packedValue,
        bottomLeft.packedValue
    )
