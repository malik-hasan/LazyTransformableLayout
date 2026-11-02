package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class TransformAnimationValuesTest {

    @Test
    fun defaultsAreTheIdentityTransform() {
        val values = TransformAnimationValues()
        assertEquals(0f, values.rotationAngle)
        assertEquals(0f, values.logZoomScale)
        assertEquals(DpOffset.Zero, values.layoutPivot)
    }

    @Test
    fun copyChangesOnlyTheGivenValue() {
        val values = TransformAnimationValues(30f, 0.5f, DpOffset(1.dp, 2.dp))
        assertEquals(TransformAnimationValues(45f, 0.5f, DpOffset(1.dp, 2.dp)), values.copy(rotationAngle = 45f))
    }
}
