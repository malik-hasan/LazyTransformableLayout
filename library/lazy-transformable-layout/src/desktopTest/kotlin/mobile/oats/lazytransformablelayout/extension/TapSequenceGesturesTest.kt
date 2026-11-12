package mobile.oats.lazytransformablelayout.extension

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TapSequenceGesturesTest {

    private fun ComposeUiTest.secondaryClicks(
        first: Offset,
        second: Offset?,
        gapMillis: Long
    ): List<Offset> {
        val doubleClicks = mutableListOf<Offset>()
        setContent {
            Box(
                Modifier
                    .size(500.dp, 700.dp)
                    .testTag("node")
                    .pointerInput(Unit) {
                        detectTapSequenceGestures(
                            onDoubleTap = {},
                            onTwoFingerTap = {},
                            onSecondaryDoubleClick = { doubleClicks += it },
                            onDoubleTapDragStopped = {},
                            onDoubleTapDrag = {}
                        )
                    }
            )
        }
        onNodeWithTag("node").performMouseInput {
            moveTo(first)
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
            if (second != null) {
                advanceEventTime(gapMillis)
                moveTo(second)
                press(MouseButton.Secondary)
                release(MouseButton.Secondary)
            }
        }
        waitForIdle()
        return doubleClicks
    }

    @Test
    fun `given two quick secondary clicks, should invoke onSecondaryDoubleClick with the second position`() =
        runComposeUiTest {
            val clicks = secondaryClicks(Offset(100f, 100f), Offset(102f, 101f), gapMillis = 50)

            assertEquals(listOf(Offset(102f, 101f)), clicks)
        }

    @Test
    fun `given a single secondary click, should not invoke onSecondaryDoubleClick`() = runComposeUiTest {
        val clicks = secondaryClicks(Offset(100f, 100f), null, gapMillis = 0)

        assertEquals(emptyList(), clicks)
    }

    @Test
    fun `given a second secondary click after the double tap timeout, should not invoke onSecondaryDoubleClick`() =
        runComposeUiTest {
            val clicks = secondaryClicks(Offset(100f, 100f), Offset(100f, 100f), gapMillis = 1_000)

            assertEquals(emptyList(), clicks)
        }

    @Test
    fun `given a second secondary click beyond touch slop, should not invoke onSecondaryDoubleClick`() =
        runComposeUiTest {
            val clicks = secondaryClicks(Offset(100f, 100f), Offset(300f, 300f), gapMillis = 50)

            assertEquals(emptyList(), clicks)
        }
}
