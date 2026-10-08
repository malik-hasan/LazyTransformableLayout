package oats.mobile.lazytransformablelayout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import oats.mobile.lazytransformablelayout.model.Item
import oats.mobile.lazytransformablelayout.model.Parallelogram
import oats.mobile.lazytransformablelayout.model.Positionable
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class SpatialIndexTest {

    private data class TestPositionable(override val bounds: DpRect, override val zIndex: Float = 0f) : Positionable

    private fun positionable(left: Int, top: Int, right: Int, bottom: Int, zIndex: Float = 0f) =
        TestPositionable(DpRect(left.dp, top.dp, right.dp, bottom.dp), zIndex)

    private val density = Density(2f)

    private fun spatialIndex(
        density: Density = this.density,
        builder: LazyTransformableLayoutScope.() -> Unit
    ) = SpatialIndex(Content(builder).intervals, density)

    private fun rectViewport(left: Float, top: Float, right: Float, bottom: Float) =
        Parallelogram(Offset(left, top), Offset(right, top), Offset(right, bottom), Offset(left, bottom))

    private fun item(index: Int, bounds: Rect, zIndex: Float) = Item(
        index = index,
        constraints = Constraints(maxWidth = bounds.width.roundToInt(), maxHeight = bounds.height.roundToInt()),
        position = bounds.topLeft,
        zIndex = zIndex
    )

    private fun SpatialIndex.query(minItemDimension: Float, viewport: Parallelogram) =
        mutableListOf<Item>().also { query(minItemDimension, viewport, it) }

    @Test
    fun emptyContentReturnsNothing() {
        assertEquals(emptyList(), spatialIndex {}.query(0f, rectViewport(-1e6f, -1e6f, 1e6f, 1e6f)))
    }

    @Test
    fun returnsIntersectingItemsInIndexOrderAcrossIntervals() {
        val index = spatialIndex {
            item(positionable(0, 0, 10, 10, zIndex = 3f)) {}
            itemsIndexed(
                listOf(
                    positionable(200, 200, 210, 210),
                    positionable(40, 40, 60, 45, zIndex = -1f),
                    positionable(50, 0, 60, 10)
                )
            ) { _, _ -> }
            item(positionable(-10, -10, 0, 0)) {}
            itemsIndexed(listOf(positionable(90, 90, 120, 120, zIndex = 2f))) { _, _ -> }
        }

        assertEquals(
            listOf(
                item(index = 0, bounds = Rect(0f, 0f, 20f, 20f), zIndex = 3f),
                item(index = 2, bounds = Rect(80f, 80f, 120f, 90f), zIndex = -1f),
                item(index = 3, bounds = Rect(100f, 0f, 120f, 20f), zIndex = 0f),
                item(index = 5, bounds = Rect(180f, 180f, 240f, 240f), zIndex = 2f)
            ),
            index.query(0f, rectViewport(0f, 0f, 200f, 200f))
        )
    }

    @Test
    fun excludesItemsSmallerThanMinItemDimension() {
        val index = spatialIndex {
            itemsIndexed(
                listOf(
                    positionable(0, 0, 4, 4),
                    positionable(10, 10, 15, 11),
                    positionable(20, 20, 21, 30)
                )
            ) { _, _ -> }
        }

        assertEquals(listOf(1, 2), index.query(10f, rectViewport(0f, 0f, 100f, 100f)).map { it.index })
        assertEquals(listOf(2), index.query(20f, rectViewport(0f, 0f, 100f, 100f)).map { it.index })
    }

    @Test
    fun respectsRotatedViewport() {
        val diamond = Parallelogram(Offset(0f, 100f), Offset(100f, 0f), Offset(200f, 100f), Offset(100f, 200f))
        val index = spatialIndex {
            itemsIndexed(
                listOf(
                    positionable(0, 0, 15, 15),
                    positionable(0, 0, 30, 30),
                    positionable(45, 45, 55, 55),
                    positionable(85, 85, 100, 100),
                    positionable(90, 0, 100, 10)
                )
            ) { _, _ -> }
        }

        assertEquals(listOf(1, 2), index.query(0f, diamond).map { it.index })
    }

    @Test
    fun matchesBruteForceForRandomItemsAndViewports() {
        val random = kotlin.random.Random(42)
        val positionables = List(500) {
            val left = random.nextInt(-500, 500)
            val top = random.nextInt(-500, 500)
            positionable(left, top, left + random.nextInt(0, 80), top + random.nextInt(0, 80), random.nextInt(-3, 3).toFloat())
        }
        val index = spatialIndex {
            itemsIndexed(positionables.subList(0, 100)) { _, _ -> }
            positionables.subList(100, 150).forEach { item(it) {} }
            itemsIndexed(positionables.subList(150, positionables.size)) { _, _ -> }
        }

        repeat(50) {
            val center = Offset(random.nextFloat() * 1000f - 500f, random.nextFloat() * 1000f - 500f)
            val u = Offset(random.nextFloat() * 400f - 200f, random.nextFloat() * 400f - 200f)
            val v = Offset(-u.y, u.x) * (random.nextFloat() + 0.5f)
            val viewport = Parallelogram(center - u - v, center + u - v, center + u + v, center - u + v)
            val minItemDimension = random.nextFloat() * 100f

            val expected = positionables.mapIndexedNotNull { i, p ->
                val bounds = with(density) { p.bounds.toRect() }
                if (bounds.maxDimension >= minItemDimension && viewport.intersects(bounds))
                    item(i, bounds, p.zIndex)
                else null
            }
            assertEquals(expected, index.query(minItemDimension, viewport))
        }
    }

    private val everything = rectViewport(-1e6f, -1e6f, 1e6f, 1e6f)

    @Test
    fun scalesBoundsByDensity() {
        val list = listOf(positionable(0, 0, 10, 10), positionable(20, 20, 40, 30, zIndex = 2f))

        assertEquals(
            listOf(
                item(index = 0, bounds = Rect(0f, 0f, 20f, 20f), zIndex = 0f),
                item(index = 1, bounds = Rect(40f, 40f, 80f, 60f), zIndex = 2f)
            ),
            spatialIndex(Density(2f)) { itemsIndexed(list) { _, _ -> } }.query(0f, everything)
        )
        assertEquals(
            listOf(
                item(index = 0, bounds = Rect(0f, 0f, 30f, 30f), zIndex = 0f),
                item(index = 1, bounds = Rect(60f, 60f, 120f, 90f), zIndex = 2f)
            ),
            spatialIndex(Density(3f)) { itemsIndexed(list) { _, _ -> } }.query(0f, everything)
        )
    }

    @Test
    fun ignoresItemsAddedToLastLayerBackingListAfterContentWasBuilt() {
        val backing = mutableListOf(positionable(0, 0, 10, 10), positionable(20, 0, 30, 10))
        val content = Content {
            item(positionable(100, 100, 110, 110)) {}
            itemsIndexed(backing) { _, _ -> }
        }
        backing += positionable(40, 0, 50, 10)
        backing += positionable(60, 0, 70, 10)

        val items = SpatialIndex(content.intervals, density).query(0f, everything)

        assertEquals(3, content.itemCount)
        assertEquals(
            listOf(
                item(index = 0, bounds = Rect(200f, 200f, 220f, 220f), zIndex = 0f),
                item(index = 1, bounds = Rect(0f, 0f, 20f, 20f), zIndex = 0f),
                item(index = 2, bounds = Rect(40f, 0f, 60f, 20f), zIndex = 0f)
            ),
            items
        )
    }

    @Test
    fun ignoresItemsAddedToEarlierLayerBackingListAfterContentWasBuilt() {
        val backing = mutableListOf(positionable(0, 0, 10, 10))
        val content = Content {
            itemsIndexed(backing) { _, _ -> }
            item(positionable(100, 100, 110, 110, zIndex = 1f)) {}
            itemsIndexed(listOf(positionable(200, 200, 210, 210, zIndex = 2f))) { _, _ -> }
        }
        repeat(5) { backing += positionable(500, 500, 510, 510, zIndex = 9f) }

        val items = SpatialIndex(content.intervals, density).query(0f, everything)

        assertEquals(3, content.itemCount)
        assertEquals(
            listOf(
                item(index = 0, bounds = Rect(0f, 0f, 20f, 20f), zIndex = 0f),
                item(index = 1, bounds = Rect(200f, 200f, 220f, 220f), zIndex = 1f),
                item(index = 2, bounds = Rect(400f, 400f, 420f, 420f), zIndex = 2f)
            ),
            items
        )
    }
}
