package oats.mobile.lazytransformablelayout.demo.thousandboxes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import oats.mobile.lazytransformablelayout.LazyTransformableLayout
import oats.mobile.lazytransformablelayout.rememberLazyTransformableLayoutState
import kotlin.random.Random

@Serializable
data object ThousandBoxes : NavKey

private const val LAYOUT_SIZE = 10000 // dp

@Composable
fun ThousandBoxes() {
    val boxes by produceState(emptyList()) {
        value = withContext(Dispatchers.Default) {
            List(1000) { id ->
                PositionableBox(
                    id = id,
                    bounds = DpRect(
                        origin = DpOffset(
                            x = Random.nextInt(0, LAYOUT_SIZE).dp,
                            y = Random.nextInt(0, LAYOUT_SIZE).dp
                        ),
                        size = DpSize(
                            width = Random.nextInt(12, 400).dp,
                            height = Random.nextInt(12, 400).dp
                        )
                    ),
                    color = Color(Random.nextLong())
                )
            }
        }
    }

    Scaffold { scaffoldPadding ->
        LazyTransformableLayout(
            modifier = Modifier.padding(scaffoldPadding),
            state = rememberLazyTransformableLayoutState(
                layoutBounds = DpRect(0.dp, 0.dp, LAYOUT_SIZE.dp, LAYOUT_SIZE.dp)
            )
        ) {
            items(boxes, key = { it.id }) { box ->
                Box(Modifier
                    .fillMaxSize()
                    .background(box.color)
                )
            }
        }
    }
}
