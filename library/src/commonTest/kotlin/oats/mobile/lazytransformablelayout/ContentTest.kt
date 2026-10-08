package oats.mobile.lazytransformablelayout

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import oats.mobile.lazytransformablelayout.model.Positionable
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
