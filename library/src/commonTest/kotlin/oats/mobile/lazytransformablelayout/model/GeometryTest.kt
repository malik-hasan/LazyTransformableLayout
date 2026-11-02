package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class GeometryTest {

    private val geometry = Geometry(
        density = 1f,
        fontScale = 1f,
        viewportSize = IntSize(1_000, 2_000),
        contentBounds = Rect(0f, 100f, 1_000f, 1_900f)
    )

    private fun assertOffsetEquals(expected: Offset, actual: Offset, tolerance: Float = 1e-2f) {
        assertEquals(expected.x, actual.x, tolerance, "x of $actual")
        assertEquals(expected.y, actual.y, tolerance, "y of $actual")
    }

    @Test
    fun contentCenterIsTheCenterOfTheContentBounds() {
        assertEquals(Offset(500f, 1_000f), geometry.contentCenter)
    }

    @Test
    fun layoutCenterMapsToTheContentCenter() {
        val center = DpOffset(300.dp, 400.dp)
        assertOffsetEquals(geometry.contentCenter, geometry.layoutToViewport(center, 25f, 2f, center))
    }

    @Test
    fun layoutToViewportScalesAndRotatesAboutTheContentCenter() {
        val center = DpOffset(0.dp, 0.dp)
        val position = DpOffset(10.dp, 20.dp)
        assertOffsetEquals(Offset(520f, 1_040f), geometry.layoutToViewport(position, 0f, 2f, center))
        assertOffsetEquals(Offset(480f, 1_010f), geometry.layoutToViewport(position, 90f, 1f, center))
    }

    @Test
    fun viewportToLayoutUndoesLayoutToViewport() {
        val center = DpOffset(120.dp, -40.dp)
        for (angle in listOf(0f, 30f, 90f, -135f)) {
            for (scale in listOf(0.5f, 1f, 4f)) {
                val position = DpOffset(75.dp, 310.dp)
                val viewportPosition = geometry.layoutToViewport(position, angle, scale, center)
                val roundTrip = geometry.viewportToLayout(viewportPosition, angle, scale, center)
                assertOffsetEquals(Offset(position.x.value, position.y.value), Offset(roundTrip.x.value, roundTrip.y.value))
            }
        }
    }

    @Test
    fun centerFromLayoutPivotKeepsTheLayoutPivotAtTheViewportPivot() {
        val layoutPivot = DpOffset(250.dp, 80.dp)
        val viewportPivot = Offset(200f, 700f)
        for (angle in listOf(0f, 45f, -100f)) {
            for (scale in listOf(0.5f, 2f)) {
                val center = geometry.centerFromLayoutPivot(viewportPivot, angle, scale, layoutPivot)
                val back = geometry.layoutToViewport(
                    layoutPivot,
                    angle,
                    scale,
                    DpOffset(center.x.dp, center.y.dp)
                )
                assertOffsetEquals(viewportPivot, back)
            }
        }
    }

    @Test
    fun centerFromTheContentCenterPivotIsTheLayoutPivot() {
        val layoutPivot = DpOffset(250.dp, 80.dp)
        assertOffsetEquals(Offset(250f, 80f), geometry.centerFromLayoutPivot(geometry.contentCenter, 33f, 3f, layoutPivot))
    }

    @Test
    fun boundsFitScaleFitsTheLimitingDimension() {
        val bounds = DpRect(0.dp, 0.dp, 200.dp, 100.dp)
        assertEquals(4.9f, geometry.boundsFitScale(bounds, 0f, 10.dp), 1e-4f)
    }

    @Test
    fun boundsFitScaleUsesTheRotatedExtent() {
        val bounds = DpRect(0.dp, 0.dp, 200.dp, 100.dp)
        assertEquals(8.9f, geometry.boundsFitScale(bounds, 90f, 10.dp), 1e-4f)
        assertEquals(
            geometry.boundsFitScale(bounds, 90f, 10.dp),
            geometry.boundsFitScale(bounds, -90f, 10.dp),
            1e-4f
        )
    }

    @Test
    fun boundsFitScaleShrinksWithMargin() {
        val bounds = DpRect(0.dp, 0.dp, 200.dp, 100.dp)
        assertEquals(5f, geometry.boundsFitScale(bounds, 0f, 0.dp), 1e-4f)
        assertEquals(true, geometry.boundsFitScale(bounds, 0f, 50.dp) < geometry.boundsFitScale(bounds, 0f, 0.dp))
    }

    @Test
    fun scaleBoundsCoverTheContentBoundsWithTheLayout() {
        val layoutBounds = DpRect(0.dp, 0.dp, 2_000.dp, 3_600.dp)
        assertEquals(0.5f..4f, geometry.scaleBoundsAt(layoutBounds, 0.1f..4f, 0f))
        assertEquals(1f..4f, geometry.scaleBoundsAt(layoutBounds, 1f..4f, 0f))
    }

    @Test
    fun scaleBoundsCollapseWhenTheCoverScaleExceedsTheZoomBounds() {
        val layoutBounds = DpRect(0.dp, 0.dp, 2_000.dp, 3_600.dp)
        assertEquals(0.5f..0.5f, geometry.scaleBoundsAt(layoutBounds, 0.1f..0.3f, 0f))
    }

    @Test
    fun panningBoundsAtNoRotationAreTheLayoutBoundsMinusHalfTheContentBounds() {
        val layoutBounds = DpRect(0.dp, 0.dp, 2_000.dp, 3_600.dp)
        val box = geometry.panningBoundsAt(layoutBounds, 0f, 1f).axisAlignedBoundingBox
        assertEquals(Rect(500f, 900f, 1_500f, 2_700f), box)
    }

    @Test
    fun viewportBoundsAtNoRotationAreTheInflatedViewportAroundTheCenter() {
        val box = geometry.viewportBounds(10f, 0f, 1f, Offset(1_000f, 1_800f)).axisAlignedBoundingBox
        assertEquals(Rect(490f, 790f, 1_510f, 2_810f), box)
    }
}
