package mobile.oats.lazytransformablelayout.extension

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import mobile.oats.lazytransformablelayout.internal.TrackpadPinchZoomElement

internal actual fun Modifier.trackpadPinchZoom(
    onZoomStopped: (position: Offset, logZoomVelocity: Float) -> Unit,
    onZoom: (position: Offset, zoomFactor: Float) -> Unit
): Modifier = this then TrackpadPinchZoomElement(onZoomStopped, onZoom)
