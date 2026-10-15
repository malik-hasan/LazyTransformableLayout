package oats.mobile.lazytransformablelayout

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import oats.mobile.lazytransformablelayout.model.Positionable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ContentTest {

    private data class TestPositionable(val id: Int) : Positionable {
        override val bounds = DpRect(0.dp, 0.dp, 10.dp, 10.dp)
    }

    private val items = List(3) { TestPositionable(it) }

    @Test
    fun emptyContentDoesNotThrow() {
        assertEquals(0, Content {}.itemCount)
    }

    @Test
    fun unkeyedItemsAlongsideKeyedItemsDoNotThrow() {
        val content = Content {
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
        val content = Content {
            items(items, key = { "same" }) {}
        }
        assertEquals(3, content.itemCount)
    }

    @Test
    fun duplicateKeysAcrossIntervalsDoNotThrow() {
        val content = Content {
            items(items, key = { it.id }) {}
            item(TestPositionable(0), key = 0) {}
            itemsIndexed(items, key = { _, item -> item.id }) { _, _ -> }
        }
        assertEquals(7, content.itemCount)
    }

    @Test
    fun keysAndContentTypesResolveToTheirItemAcrossIntervals() {
        val content = Content {
            item(TestPositionable(9), key = "single", contentType = "single-type") {}
            itemsIndexed(
                items,
                key = { i, item -> "indexed-$i-${item.id}" },
                contentType = { i, _ -> "type-$i" }
            ) { _, _ -> }
            items(items, key = { "plain-${it.id}" }) {}
        }

        assertEquals(
            listOf("single", "indexed-0-0", "indexed-1-1", "indexed-2-2", "plain-0", "plain-1", "plain-2"),
            List(content.itemCount) { content.getKey(it) }
        )
        assertEquals(
            listOf("single-type", "type-0", "type-1", "type-2", null, null, null),
            List(content.itemCount) { content.getContentType(it) }
        )
    }

    @Test
    fun intervalsCarryTheirItemsSizesAndStartIndices() {
        val single = TestPositionable(9)
        val second = TestPositionable(10)
        val content = Content {
            item(single) {}
            itemsIndexed(items) { _, _ -> }
            items(emptyList<TestPositionable>()) {}
            item(second) {}
            items(items) {}
        }

        val intervals = buildList { content.intervals.forEach { add(it) } }
        assertEquals(listOf(0, 1, 4, 5), intervals.map { it.startIndex })
        assertEquals(listOf(1, 3, 1, 3), intervals.map { it.size })
        assertEquals(listOf(listOf(single), items, listOf(second), items), intervals.map { it.value.items })
        assertSame(items, intervals[1].value.items)
        assertEquals(8, content.itemCount)
    }

    @Test
    fun itemCountIsFixedWhenBackingListGrowsAfterBuild() {
        val backing = mutableListOf(TestPositionable(0), TestPositionable(1))
        val content = Content {
            itemsIndexed(backing) { _, _ -> }
            item(TestPositionable(9)) {}
        }
        repeat(5) { backing += TestPositionable(100 + it) }

        assertEquals(3, content.itemCount)
        assertEquals(2, content.intervals[0].size)
        assertEquals(2, content.intervals[2].startIndex)
        assertEquals(1, content.intervals[2].size)
    }
}
