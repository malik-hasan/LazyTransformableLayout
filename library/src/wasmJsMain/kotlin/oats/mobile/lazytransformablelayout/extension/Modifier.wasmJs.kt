package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

internal actual fun Modifier.trackpadPinchZoom(
    onZoomStopped: (position: Offset, logZoomVelocity: Float) -> Unit,
    onZoom: (position: Offset, zoomFactor: Float) -> Unit
) = this
