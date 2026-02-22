package oats.mobile.lazytransformablelayout.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.size
import oats.mobile.lazytransformablelayout.LazyTransformableLayout
import oats.mobile.lazytransformablelayout.LazyTransformableLayoutState
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val layoutSize = 10000 // dp

            val boxes = remember {
                mutableStateListOf(
                    *(1..1000).map {
                        TestPositionable(
                            bounds = DpRect(
                                origin = DpOffset(
                                    x = Random.nextInt(0, layoutSize).dp,
                                    y = Random.nextInt(0, layoutSize).dp
                                ),
                                size = DpSize(
                                    width = Random.nextInt(12, 256).dp,
                                    height = Random.nextInt(12, 256).dp
                                )
                            ),
                            color = Color(Random.nextLong())
                        )
                    }.toTypedArray()
                )
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
                            .size(box.bounds.size)
                            .background(box.color)
                        )
                    }
                }
            }
        }
    }
}
