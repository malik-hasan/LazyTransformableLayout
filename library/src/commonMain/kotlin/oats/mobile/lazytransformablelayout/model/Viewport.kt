package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize

internal data class Viewport(
    private val densityDelegate: Density,
    val size: IntSize,
    val contentBounds: Rect
): Density by densityDelegate
