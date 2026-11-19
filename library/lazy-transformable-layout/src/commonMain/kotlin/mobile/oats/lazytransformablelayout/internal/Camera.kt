package mobile.oats.lazytransformablelayout.internal

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import mobile.oats.lazytransformablelayout.extension.toPx

internal data class Camera(
    val rotationAngle: Float,
    val zoomScale: Float,
    val centerOffset: DpOffset
) {
    context(density: Density)
    val centerOffsetPx
        get() = centerOffset.toPx()
}
