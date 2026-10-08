package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertPositionInRootIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import oats.mobile.lazytransformablelayout.model.Positionable
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LazyTransformableLayoutContentChangeTest {

    private data class TestItem(val key: String, val offset: DpOffset, val size: DpSize, val zIndex: Float = 0f)

    private val manyItems = List(200) { i ->
        TestItem(
            key = "old$i",
            offset = DpOffset((i % 20 * 100).dp, (i / 20 * 100).dp),
            size = DpSize(100.dp, 100.dp)
        )
    }

    private val fewItems = listOf(
        TestItem("new0", DpOffset(10.dp, 20.dp), DpSize(50.dp, 60.dp)),
        TestItem("new1", DpOffset(300.dp, 150.dp), DpSize(80.dp, 40.dp)),
        TestItem("new2", DpOffset(1800.dp, 1800.dp), DpSize(50.dp, 50.dp))
    )

    private fun hasTagStartingWith(prefix: String) = SemanticsMatcher("TestTag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun ComposeUiTest.assertShowsOnlyManyItems() {
        onNodeWithTag("old0").assertPositionInRootIsEqualTo(0.dp, 0.dp)
        onNodeWithTag("old21").assertPositionInRootIsEqualTo(100.dp, 100.dp)
        onAllNodes(hasTagStartingWith("new")).assertCountEquals(0)
    }

    private fun ComposeUiTest.assertShowsOnlyFewItems() {
        onAllNodes(hasTagStartingWith("old")).assertCountEquals(0)
        onNodeWithTag("new0").assertPositionInRootIsEqualTo(10.dp, 20.dp)
        onNodeWithTag("new1").assertPositionInRootIsEqualTo(300.dp, 150.dp)
        onAllNodesWithTag("new2").assertCountEquals(0)
    }

    private fun ComposeUiTest.setLayout(items: () -> List<TestItem>, colors: Map<String, Color> = emptyMap()) {
        val state = LazyTransformableLayoutState(layoutBounds = Rect(0f, 0f, 2000f, 2000f))
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(500.dp)) {
                    LazyTransformableLayout(state = state, overscrollEffect = null) {
                        items().forEach { item ->
                            item(offset = item.offset, size = item.size, zIndex = item.zIndex, key = item.key) {
                                Box(
                                    Modifier
                                        .size(item.size)
                                        .background(colors[item.key] ?: Color.Transparent)
                                        .testTag(item.key)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun changedContentIsDisplayedOnTheNextFrame() = runComposeUiTest {
        var items by mutableStateOf(manyItems)
        setLayout({ items })

        waitForIdle()
        assertShowsOnlyManyItems()

        mainClock.autoAdvance = false
        items = fewItems
        mainClock.advanceTimeByFrame()
        assertShowsOnlyFewItems()

        items = manyItems
        mainClock.advanceTimeByFrame()
        assertShowsOnlyManyItems()
    }

    private fun ComposeUiTest.colorAt(x: Int, y: Int) = onRoot().captureToImage().toPixelMap()[x, y]

    @Test
    fun itemsWithEqualZIndexAreDrawnInIndexOrder() = runComposeUiTest {
        setLayout(
            items = {
                listOf(
                    TestItem("a", DpOffset(0.dp, 0.dp), DpSize(100.dp, 100.dp)),
                    TestItem("b", DpOffset(50.dp, 50.dp), DpSize(100.dp, 100.dp))
                )
            },
            colors = mapOf("a" to Color.Red, "b" to Color.Blue)
        )
        assertEquals(Color.Red, colorAt(25, 25))
        assertEquals(Color.Blue, colorAt(75, 75))
    }

    @Test
    fun higherZIndexIsDrawnAboveRegardlessOfIndex() = runComposeUiTest {
        setLayout(
            items = {
                listOf(
                    TestItem("a", DpOffset(0.dp, 0.dp), DpSize(100.dp, 100.dp), zIndex = 1f),
                    TestItem("b", DpOffset(50.dp, 50.dp), DpSize(100.dp, 100.dp))
                )
            },
            colors = mapOf("a" to Color.Red, "b" to Color.Blue)
        )
        assertEquals(Color.Red, colorAt(75, 75))
        assertEquals(Color.Blue, colorAt(125, 125))
    }

    private data class PositionedItem(val key: String, override val bounds: DpRect) : Positionable

    private fun positioned(key: String, left: Int, top: Int) =
        PositionedItem(key, DpRect(DpOffset(left.dp, top.dp), DpSize(50.dp, 50.dp)))

    private class MovableItem(initial: DpRect) : Positionable {
        override var bounds by mutableStateOf(initial)
    }

    private fun <T : Positionable> ComposeUiTest.setPositionableLayout(
        indexed: Boolean,
        key: (T) -> String,
        items: @Composable () -> List<T>
    ) {
        val state = LazyTransformableLayoutState(layoutBounds = Rect(0f, 0f, 2000f, 2000f))
        setContent {
            val list = items()
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(500.dp)) {
                    LazyTransformableLayout(state = state, overscrollEffect = null) {
                        if (indexed)
                            itemsIndexed(list, key = { _, item -> key(item) }) { _, item ->
                                Box(Modifier.size(50.dp).testTag("item-${key(item)}"))
                            }
                        else
                            items(list, key = key) { item ->
                                Box(Modifier.size(50.dp).testTag("item-${key(item)}"))
                            }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.assertShowsExactly(vararg expected: Pair<String, DpOffset>) {
        waitForIdle()
        onAllNodes(hasTagStartingWith("item-")).assertCountEquals(expected.size)
        expected.forEach { (key, offset) ->
            onNodeWithTag("item-$key").assertPositionInRootIsEqualTo(offset.x, offset.y)
        }
    }

    private fun ComposeUiTest.mutatesStateListInPlace(indexed: Boolean) {
        lateinit var list: SnapshotStateList<PositionedItem>
        setPositionableLayout(indexed, key = { it.key }) {
            list = remember { mutableStateListOf(positioned("a", 0, 0), positioned("b", 100, 0)) }
            list
        }
        assertShowsExactly("a" to DpOffset(0.dp, 0.dp), "b" to DpOffset(100.dp, 0.dp))

        list.add(positioned("c", 200, 50))
        assertShowsExactly("a" to DpOffset(0.dp, 0.dp), "b" to DpOffset(100.dp, 0.dp), "c" to DpOffset(200.dp, 50.dp))

        list.removeAt(0)
        assertShowsExactly("b" to DpOffset(100.dp, 0.dp), "c" to DpOffset(200.dp, 50.dp))

        list[0] = positioned("b", 300, 250)
        assertShowsExactly("b" to DpOffset(300.dp, 250.dp), "c" to DpOffset(200.dp, 50.dp))
    }

    @Test
    fun inPlaceMutatedStateListPassedToItemsIsDisplayed() = runComposeUiTest {
        mutatesStateListInPlace(indexed = false)
    }

    @Test
    fun inPlaceMutatedStateListPassedToItemsIndexedIsDisplayed() = runComposeUiTest {
        mutatesStateListInPlace(indexed = true)
    }

    @Test
    fun stateBackedBoundsChangeMovesItem() = runComposeUiTest {
        val movable = MovableItem(DpRect(DpOffset(10.dp, 10.dp), DpSize(50.dp, 50.dp)))
        val list = listOf(movable)
        setPositionableLayout(indexed = true, key = { "movable" }) { list }
        assertShowsExactly("movable" to DpOffset(10.dp, 10.dp))

        movable.bounds = DpRect(DpOffset(250.dp, 300.dp), DpSize(50.dp, 50.dp))
        assertShowsExactly("movable" to DpOffset(250.dp, 300.dp))

        movable.bounds = DpRect(DpOffset(1000.dp, 1000.dp), DpSize(50.dp, 50.dp))
        assertShowsExactly()
    }

    private fun ComposeUiTest.assertPositionInRootPx(tag: String, x: Float, y: Float) =
        assertEquals(Offset(x, y), onNodeWithTag(tag).fetchSemanticsNode().positionInRoot)

    @Test
    fun densityChangeMovesItemsOnTheNextFrame() = runComposeUiTest {
        var density by mutableStateOf(Density(1f))
        val state = LazyTransformableLayoutState(layoutBounds = Rect(0f, 0f, 2000f, 2000f))
        setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                Box(Modifier.size(500.dp)) {
                    LazyTransformableLayout(state = state, overscrollEffect = null) {
                        fewItems.forEach { item ->
                            item(offset = item.offset, size = item.size, key = item.key) {
                                Box(Modifier.size(item.size).testTag(item.key))
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        assertPositionInRootPx("new0", 10f, 20f)
        assertPositionInRootPx("new1", 300f, 150f)

        mainClock.autoAdvance = false
        density = Density(2f)
        mainClock.advanceTimeByFrame()
        assertPositionInRootPx("new0", 20f, 40f)
        assertPositionInRootPx("new1", 600f, 300f)

        density = Density(2f, fontScale = 3f)
        mainClock.advanceTimeByFrame()
        assertPositionInRootPx("new0", 20f, 40f)
        assertPositionInRootPx("new1", 600f, 300f)

        density = Density(1.5f, fontScale = 3f)
        mainClock.advanceTimeByFrame()
        assertPositionInRootPx("new0", 15f, 30f)
        assertPositionInRootPx("new1", 450f, 225f)
    }
}
