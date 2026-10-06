package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import oats.mobile.lazytransformablelayout.extension.cross
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParallelogramTest {

    // a square rotated 45 degrees, centered on the origin
    private val diamond = Parallelogram(
        Offset(-1f, 0f),
        Offset(0f, -1f),
        Offset(1f, 0f),
        Offset(0f, 1f)
    )

    @Test
    fun boundsCoverAllVertices() {
        assertEquals(Rect(-1f, -1f, 1f, 1f), diamond.bounds)
    }

    private val inner = Parallelogram(Offset(-0.5f, 0f), Offset(0f, -0.5f), Offset(0.5f, 0f), Offset(0f, 0.5f))

    @Test
    fun containsParallelogram() {
        val shifted = Parallelogram(Offset(0f, 0f), Offset(1f, -1f), Offset(2f, 0f), Offset(1f, 1f))
        assertTrue(inner in diamond)
        assertFalse(diamond in inner)
        assertFalse(shifted in diamond)
    }

    @Test
    fun containsItself() {
        assertTrue(diamond in diamond)
    }

    @Test
    fun containsRequiresEveryVertex() {
        // three vertices inside the diamond, one past its edge
        val straddling = Parallelogram(Offset(0f, 0f), Offset(0.4f, 0f), Offset(0.8f, 0.4f), Offset(0.4f, 0.4f))
        assertFalse(straddling in diamond)
    }

    @Test
    fun containsIgnoresWindingDirection() {
        val counterClockwise = Parallelogram(diamond[0], diamond[3], diamond[2], diamond[1])
        assertTrue(inner in counterClockwise)
        assertFalse(diamond in inner)
    }

    @Test
    fun degenerateContainsNothing() {
        val line = Parallelogram(Offset(0f, 0f), Offset(1f, 1f), Offset(2f, 2f), Offset(1f, 1f))
        val point = Offset(1f, 1f)
        assertFalse(Parallelogram(point, point, point, point) in line)
    }

    @Test
    fun intersectsRect() {
        // inside the bounds but past the diagonal edge
        assertFalse(diamond.intersects(Rect(0.6f, 0.6f, 1f, 1f)))
        // crosses the diagonal edge
        assertTrue(diamond.intersects(Rect(0.4f, 0.4f, 1f, 1f)))
        // encloses the diamond
        assertTrue(diamond.intersects(Rect(-5f, -5f, 5f, 5f)))
        // inside the diamond
        assertTrue(diamond.intersects(Rect(-0.1f, -0.1f, 0.1f, 0.1f)))
        // outside the bounds
        assertFalse(diamond.intersects(Rect(2f, 2f, 3f, 3f)))
    }

    @Test
    fun intersectsMatchesPolygonOverlap() {
        val random = Random(0)
        repeat(20_000) {
            val origin = Offset(random.nextFloat() * 10 - 5, random.nextFloat() * 10 - 5)
            val u = Offset(random.nextFloat() * 6 - 3, random.nextFloat() * 6 - 3)
            val v = Offset(random.nextFloat() * 6 - 3, random.nextFloat() * 6 - 3)
            if ((u cross v) == 0f) return@repeat
            val parallelogram = Parallelogram(origin, origin + u, origin + u + v, origin + v)

            val left = random.nextFloat() * 14 - 7
            val top = random.nextFloat() * 14 - 7
            val rect = Rect(left, top, left + random.nextFloat() * 4, top + random.nextFloat() * 4)

            assertEquals(
                polygonsOverlap(parallelogram.corners, rect.corners),
                parallelogram.intersects(rect),
                "$parallelogram vs $rect"
            )
        }
    }

    private val Parallelogram.corners
        get() = List(4) { this[it] }

    private val Rect.corners
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Reference overlap test for convex polygons: a vertex of one inside the other, or crossing edges */
    private fun polygonsOverlap(a: List<Offset>, b: List<Offset>) =
        a.any { insideConvex(it, b) } || b.any { insideConvex(it, a) } ||
            a.indices.any { i ->
                b.indices.any { j ->
                    segmentsCross(a[i], a[(i + 1) % a.size], b[j], b[(j + 1) % b.size])
                }
            }

    private fun insideConvex(point: Offset, polygon: List<Offset>): Boolean {
        val sides = polygon.indices.map { i ->
            (polygon[(i + 1) % polygon.size] - polygon[i]) cross (point - polygon[i])
        }
        return sides.all { it >= 0f } || sides.all { it <= 0f }
    }

    private fun segmentsCross(a: Offset, b: Offset, c: Offset, d: Offset): Boolean {
        val d1 = (b - a) cross (c - a)
        val d2 = (b - a) cross (d - a)
        val d3 = (d - c) cross (a - c)
        val d4 = (d - c) cross (b - c)
        return d1 * d2 < 0 && d3 * d4 < 0
    }
}
