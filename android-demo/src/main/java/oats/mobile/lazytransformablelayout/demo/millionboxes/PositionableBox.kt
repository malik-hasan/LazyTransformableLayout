package oats.mobile.lazytransformablelayout.demo.millionboxes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpRect
import oats.mobile.lazytransformablelayout.model.Positionable

data class PositionableBox(
    override val bounds: DpRect,
    val color: Color
) : Positionable
