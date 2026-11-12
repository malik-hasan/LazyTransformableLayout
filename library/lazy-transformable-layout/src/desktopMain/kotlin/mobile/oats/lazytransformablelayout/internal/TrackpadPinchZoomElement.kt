package mobile.oats.lazytransformablelayout.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.ModifierNodeElement

internal data class TrackpadPinchZoomElement(
    val onZoomStopped: (position: Offset, logZoomVelocity: Float) -> Unit,
    val onZoom: (position: Offset, zoomFactor: Float) -> Unit
) : ModifierNodeElement<TrackpadPinchZoomNode>() {
    override fun create() = TrackpadPinchZoomNode(onZoomStopped, onZoom)

    override fun update(node: TrackpadPinchZoomNode) {
        node.onZoomStopped = onZoomStopped
        node.onZoom = onZoom
    }
}
