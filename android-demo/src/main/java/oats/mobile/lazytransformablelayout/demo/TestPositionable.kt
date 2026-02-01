package oats.mobile.lazytransformablelayout.demo

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpRect
import oats.mobile.lazytransformablelayout.model.Positionable

data class TestPositionable(
    override val bounds: DpRect,
    val color: Color
) : Positionable
