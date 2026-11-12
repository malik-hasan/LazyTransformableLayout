package mobile.oats.lazytransformablelayout.extension

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class DpRectTest {

    @Test
    fun `given an offset rect when computing the center should be the midpoint of its edges`() {
        assertEquals(DpOffset(1_200.dp, 1_100.dp), DpRect(1_000.dp, 1_000.dp, 1_400.dp, 1_200.dp).center)
    }

    @Test
    fun `given a rect at the origin when computing the center should be half its size`() {
        assertEquals(DpOffset(200.dp, 100.dp), DpRect(0.dp, 0.dp, 400.dp, 200.dp).center)
    }

    @Test
    fun `given a rect with negative coordinates when computing the center should be the midpoint of its edges`() {
        assertEquals(DpOffset(-50.dp, 0.dp), DpRect(-100.dp, -20.dp, 0.dp, 20.dp).center)
    }
}
