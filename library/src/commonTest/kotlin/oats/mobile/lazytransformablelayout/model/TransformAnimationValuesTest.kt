package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.unit.DpOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class TransformAnimationValuesTest {

    @Test
    fun `given default values should be the identity transform`() {
        val values = TransformAnimationValues()
        assertEquals(0f, values.rotationAngle)
        assertEquals(0f, values.logZoomScale)
        assertEquals(DpOffset.Zero, values.layoutPivot)
    }
}
