package mobile.oats.lazytransformablelayout.extension

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class OffsetTest {

    private fun assertOffsetEquals(expected: Offset, actual: Offset, tolerance: Float = 1e-3f) {
        assertEquals(expected.x, actual.x, tolerance, "x of $actual")
        assertEquals(expected.y, actual.y, tolerance, "y of $actual")
    }

    @Test
    fun `given a point beside a segment when finding the nearest point should project onto the segment interior`() {
        val nearest = Offset(3f, 4f).nearestPointOnSegment(Offset(0f, 0f), Offset(10f, 0f))
        assertOffsetEquals(Offset(3f, 0f), nearest)
    }

    @Test
    fun `given a point beyond a segment when finding the nearest point should clamp to the endpoints`() {
        val p = Offset(0f, 0f)
        val q = Offset(10f, 0f)
        assertOffsetEquals(p, Offset(-5f, 2f).nearestPointOnSegment(p, q))
        assertOffsetEquals(q, Offset(15f, -2f).nearestPointOnSegment(p, q))
    }

    @Test
    fun `given a degenerate segment when finding the nearest point should return its point`() {
        val p = Offset(2f, 3f)
        assertOffsetEquals(p, Offset(9f, 9f).nearestPointOnSegment(p, p))
    }

    @Test
    fun `given two points should compute the squared Euclidean distance`() {
        assertEquals(25f, Offset(1f, 1f).distanceSquared(Offset(4f, 5f)))
        assertEquals(0f, Offset(1f, 1f).distanceSquared(Offset(1f, 1f)))
    }

    @Test
    fun `given a vector pointing clockwise on screen when computing the cross product should be positive`() {
        assertEquals(1f, Offset(1f, 0f) cross Offset(0f, 1f))
        assertEquals(-1f, Offset(0f, 1f) cross Offset(1f, 0f))
        assertEquals(0f, Offset(2f, 4f) cross Offset(1f, 2f))
    }

    @Test
    fun `given two vectors when computing the dot product should multiply components`() {
        assertEquals(11f, Offset(1f, 2f) dot Offset(3f, 4f))
        assertEquals(0f, Offset(1f, 0f) dot Offset(0f, 5f))
    }

    @Test
    fun `given default arguments when transforming should be the identity`() {
        assertOffsetEquals(Offset(3f, -4f), Offset(3f, -4f).transform())
    }

    @Test
    fun `given a scale and an offset when transforming should scale then subtract the offset`() {
        val result = Offset(3f, 4f).transform(scale = 2f, offset = Offset(1f, 1f))
        assertOffsetEquals(Offset(5f, 7f), result)
    }

    @Test
    fun `given a rotation when transforming should rotate clockwise on screen`() {
        assertOffsetEquals(Offset(0f, 1f), Offset(1f, 0f).transform(angle = 90f))
        assertOffsetEquals(Offset(-1f, 0f), Offset(1f, 0f).transform(angle = 180f))
    }

    @Test
    fun `given a rotation and an offset when transforming should rotate then subtract the offset`() {
        val result = Offset(3f, 1f).transform(angle = 90f, offset = Offset(1f, 1f))
        assertOffsetEquals(Offset(-2f, 2f), result)
    }

    @Test
    fun `given a scale and a rotation when converting to viewport axes should scale then rotate`() {
        assertOffsetEquals(Offset(0f, 2f), Offset(1f, 0f).toViewportAxes(scale = 2f, angle = 90f))
    }

    @Test
    fun `given viewport axes when converting to layout axes should undo the conversion to viewport axes`() {
        for (angle in listOf(0f, 30f, 90f, -135f, 200f)) {
            for (scale in listOf(0.25f, 1f, 3f)) {
                val original = Offset(12f, -7f)
                val roundTrip = original.toViewportAxes(scale, angle).toLayoutAxes(scale, angle)
                assertOffsetEquals(original, roundTrip)
            }
        }
    }

    @Test
    fun `given a scale when converting to layout axes should divide by the scale`() {
        assertOffsetEquals(Offset(5f, 0f), Offset(10f, 0f).toLayoutAxes(scale = 2f, angle = 0f))
    }

    @Test
    fun `given a density when converting to dp should divide by the density`() {
        val dp = with(Density(2f)) { Offset(10f, 30f).toDp() }
        assertEquals(DpOffset(5.dp, 15.dp), dp)
    }
}
