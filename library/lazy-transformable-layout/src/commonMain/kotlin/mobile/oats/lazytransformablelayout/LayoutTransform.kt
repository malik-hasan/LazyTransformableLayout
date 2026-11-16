package mobile.oats.lazytransformablelayout

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import mobile.oats.lazytransformablelayout.extension.toPx

internal data class LayoutTransform(
    val rotationAngle: Float = 0f,
    val zoomScale: Float = 1f,
    val centerOffset: DpOffset = DpOffset.Zero
) {
    context(density: Density)
    val centerOffsetPx
        get() = centerOffset.toPx()
}
