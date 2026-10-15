package oats.mobile.lazytransformablelayout

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import oats.mobile.lazytransformablelayout.model.Item
import oats.mobile.lazytransformablelayout.model.Positionable
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentTest {

    private data class TestPositionable(val id: Int) : Positionable {
        override val bounds = DpRect(0.dp, 0.dp, 10.dp, 10.dp)
    }

    private data class BoundedPositionable(override val bounds: DpRect, override val zIndex: Float = 0f) : Positionable

    private fun positionable(left: Int, top: Int, right: Int, bottom: Int, zIndex: Float = 0f) =
        BoundedPositionable(DpRect(left.dp, top.dp, right.dp, bottom.dp), zIndex)

    private val density = Density(2f)

    private val items = List(3) { TestPositionable(it) }

    @Test
    fun emptyContentDoesNotThrow() {
        assertEquals(0, Content(density) {}.itemCount)
    }

    @Test
    fun unkeyedItemsAlongsideKeyedItemsDoNotThrow() {
        val content = Content(density) {
            items(items) {}
            items(items, key = { it.id }) {}
            item(TestPositionable(9)) {}
            item(TestPositionable(10), key = "single") {}
            items(items) {}
        }
        assertEquals(11, content.itemCount)
    }

    @Test
    fun duplicateKeysWithinOneIntervalDoNotThrow() {
        val content = Content(density) {
            items(items, key = { "same" }) {}
        }
        assertEquals(3, content.itemCount)
    }

    @Test
    fun duplicateKeysAcrossIntervalsDoNotThrow() {
        val content = Content(density) {
            items(items, key = { it.id }) {}
            item(TestPositionable(0), key = 0) {}
            itemsIndexed(items, key = { _, item -> item.id }) { _, _ -> }
        }
        assertEquals(7, content.itemCount)
    }

    @Test
    fun emptyContentHasNoItems() {
        assertEquals(emptyList(), Content(density) {}.items)
    }

    @Test
    fun itemsHaveGlobalIndicesInOrderAcrossIntervals() {
        val content = Content(density) {
            item(positionable(0, 0, 10, 10, zIndex = 3f)) {}
            itemsIndexed(
                listOf(
                    positionable(200, 200, 210, 210),
                    positionable(40, 40, 60, 45, zIndex = -1f)
                )
            ) { _, _ -> }
            item(positionable(-10, -10, 0, 0)) {}
            itemsIndexed(listOf(positionable(90, 90, 120, 120, zIndex = 2f))) { _, _ -> }
        }

        assertEquals(
            listOf(
                Item(index = 0, bounds = Rect(0f, 0f, 20f, 20f), zIndex = 3f),
                Item(index = 1, bounds = Rect(400f, 400f, 420f, 420f), zIndex = 0f),
                Item(index = 2, bounds = Rect(80f, 80f, 120f, 90f), zIndex = -1f),
                Item(index = 3, bounds = Rect(-20f, -20f, 0f, 0f), zIndex = 0f),
                Item(index = 4, bounds = Rect(180f, 180f, 240f, 240f), zIndex = 2f)
            ),
            content.items
        )
    }

    @Test
    fun itemBoundsAreScaledByDensity() {
        val list = listOf(positionable(0, 0, 10, 10), positionable(20, 20, 40, 30, zIndex = 2f))

        assertEquals(
            listOf(
                Item(index = 0, bounds = Rect(0f, 0f, 10f, 10f), zIndex = 0f),
                Item(index = 1, bounds = Rect(20f, 20f, 40f, 30f), zIndex = 2f)
            ),
            Content(Density(1f)) { itemsIndexed(list) { _, _ -> } }.items
        )
        assertEquals(
            listOf(
                Item(index = 0, bounds = Rect(0f, 0f, 30f, 30f), zIndex = 0f),
                Item(index = 1, bounds = Rect(60f, 60f, 120f, 90f), zIndex = 2f)
            ),
            Content(Density(3f)) { itemsIndexed(list) { _, _ -> } }.items
        )
    }

    @Test
    fun ignoresItemsAddedToLastLayerBackingListAfterContentWasBuilt() {
        val backing = mutableListOf(positionable(0, 0, 10, 10), positionable(20, 0, 30, 10))
        val content = Content(density) {
            item(positionable(100, 100, 110, 110)) {}
            itemsIndexed(backing) { _, _ -> }
        }
        backing += positionable(40, 0, 50, 10)
        backing += positionable(60, 0, 70, 10)

        assertEquals(3, content.itemCount)
        assertEquals(
            listOf(
                Item(index = 0, bounds = Rect(200f, 200f, 220f, 220f), zIndex = 0f),
                Item(index = 1, bounds = Rect(0f, 0f, 20f, 20f), zIndex = 0f),
                Item(index = 2, bounds = Rect(40f, 0f, 60f, 20f), zIndex = 0f)
            ),
            content.items
        )
    }

    @Test
    fun ignoresItemsAddedToEarlierLayerBackingListAfterContentWasBuilt() {
        val backing = mutableListOf(positionable(0, 0, 10, 10))
        val content = Content(density) {
            itemsIndexed(backing) { _, _ -> }
            item(positionable(100, 100, 110, 110, zIndex = 1f)) {}
            itemsIndexed(listOf(positionable(200, 200, 210, 210, zIndex = 2f))) { _, _ -> }
        }
        repeat(5) { backing += positionable(500, 500, 510, 510, zIndex = 9f) }

        assertEquals(3, content.itemCount)
        assertEquals(
            listOf(
                Item(index = 0, bounds = Rect(0f, 0f, 20f, 20f), zIndex = 0f),
                Item(index = 1, bounds = Rect(200f, 200f, 220f, 220f), zIndex = 1f),
                Item(index = 2, bounds = Rect(400f, 400f, 420f, 420f), zIndex = 2f)
            ),
            content.items
        )
    }
}
