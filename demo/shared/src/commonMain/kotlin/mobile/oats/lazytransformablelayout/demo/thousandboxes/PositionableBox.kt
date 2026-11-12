package mobile.oats.lazytransformablelayout.demo.thousandboxes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpRect
import mobile.oats.lazytransformablelayout.Positionable

data class PositionableBox(
    val id: Int,
    override val bounds: DpRect,
    val color: Color
) : Positionable
