package oats.mobile.lazytransformablelayout.extension

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

internal expect fun Modifier.trackpadPinchZoom(onZoom: (position: Offset, zoomFactor: Float) -> Unit): Modifier
