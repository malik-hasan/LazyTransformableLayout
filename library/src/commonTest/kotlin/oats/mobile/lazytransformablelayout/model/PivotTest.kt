package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PivotTest {

    private val geometry = Geometry(
        density = 1f,
        fontScale = 1f,
        viewportSize = IntSize(1_000, 2_000),
        contentBounds = Rect(0f, 100f, 1_000f, 1_900f)
    )

    private val center = DpOffset(300.dp, 400.dp)

    @Test
    fun viewportPivotIsItsPositionRegardlessOfTheTransform() {
        with(geometry) {
            val pivot = Pivot.Viewport(Offset(123f, 456f))
            assertEquals(Offset(123f, 456f), pivot.viewportPosition(0f, 1f, center))
            assertEquals(Offset(123f, 456f), pivot.viewportPosition(77f, 3f, center))
        }
    }

    @Test
    fun contentCenterPivotIsTheCenterOfTheContentArea() {
        with(geometry) {
            assertEquals(Offset(500f, 1_000f), Pivot.ContentCenter.viewportPosition(45f, 2f, center))
        }
    }

    @Test
    fun layoutPivotIsWhereItsPositionCurrentlyIsInTheViewport() {
        with(geometry) {
            val pivot = Pivot.Layout(DpOffset(310.dp, 420.dp))
            val position = pivot.viewportPosition(0f, 2f, center)
            assertEquals(520f, position.x, 1e-3f)
            assertEquals(1_040f, position.y, 1e-3f)
        }
    }

    @Test
    fun layoutPivotAtTheCenterOffsetIsTheContentCenter() {
        with(geometry) {
            val position = Pivot.Layout(center).viewportPosition(60f, 3f, center)
            assertEquals(500f, position.x, 1e-3f)
            assertEquals(1_000f, position.y, 1e-3f)
        }
    }

    @Test
    fun pivotsCompareByValue() {
        assertEquals(Pivot.Viewport(Offset(1f, 2f)), Pivot.Viewport(Offset(1f, 2f)))
        assertNotEquals<Pivot>(Pivot.Viewport(Offset(1f, 2f)), Pivot.Viewport(Offset(2f, 1f)))
        assertEquals(Pivot.Layout(DpOffset(1.dp, 2.dp)), Pivot.Layout(DpOffset(1.dp, 2.dp)))
        assertNotEquals<Pivot>(Pivot.ContentCenter, Pivot.Layout(DpOffset(1.dp, 2.dp)))
    }
}
