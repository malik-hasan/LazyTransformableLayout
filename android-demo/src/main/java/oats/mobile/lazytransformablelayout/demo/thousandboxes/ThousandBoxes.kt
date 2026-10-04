package oats.mobile.lazytransformablelayout.demo.thousandboxes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import oats.mobile.lazytransformablelayout.LazyTransformableLayout
import oats.mobile.lazytransformablelayout.LazyTransformableLayoutState
import kotlin.random.Random

@Serializable
data object ThousandBoxes : NavKey

@Composable
fun ThousandBoxes() {

    val scope = rememberCoroutineScope()
    val layoutSize = 10000

    val boxes by produceState(emptyList()) {
        scope.launch(Dispatchers.Default) {
            value = (1..1000).map {
                PositionableBox(
                    bounds = DpRect(
                        origin = DpOffset(
                            x = Random.nextInt(0, layoutSize).dp,
                            y = Random.nextInt(0, layoutSize).dp
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
        val density = LocalDensity.current
        LazyTransformableLayout(
            modifier = Modifier.padding(scaffoldPadding),
            state = remember {
                LazyTransformableLayoutState(
                    layoutBounds = Rect(
                        offset = Offset(0f, 0f),
                        size = with(density) {
                            Size(layoutSize.dp.toPx(), layoutSize.dp.toPx())
                        }
                    ),
                )
            }
        ) {
            items(boxes) { box ->
                Box(Modifier
                    .fillMaxSize()
                    .background(box.color)
                )
            }
        }
    }
}
