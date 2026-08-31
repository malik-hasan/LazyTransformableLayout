package oats.mobile.lazytransformablelayout.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import oats.mobile.lazytransformablelayout.LazyTransformableLayout
import oats.mobile.lazytransformablelayout.LazyTransformableLayoutState
import oats.mobile.lazytransformablelayout.model.Positionable
import kotlin.random.Random

@Serializable
data object Quadrants : NavKey

@Composable
fun Quadrants() {
    val layoutSize = 10000 // dp

    Scaffold { scaffoldPadding ->
        val density = LocalDensity.current
        LazyTransformableLayout(
            modifier = Modifier.padding(scaffoldPadding),
            state = remember {
                LazyTransformableLayoutState(
                    layoutBounds = Rect(
                        offset = Offset.Zero,
                        size = with(density) {
                            Size(layoutSize.dp.toPx(), layoutSize.dp.toPx())
                        }
                    ),
                )
            }
        ) {
            itemsIndexed(
                buildList {
                    var left = 0.dp
                    var top = 0.dp
                    var right = 2500.dp
                    var bottom = 2500.dp
                    (1..16).forEach { i ->
                        add(
                            object : Positionable {
                                override val bounds = DpRect(
                                    left,
                                    top,
                                    right,
                                    bottom,
                                )
                            }
                        )

                        if (i % 4 == 0) {
                            left = 0.dp
                            top += 2500.dp
                            right = 2500.dp
                            bottom += 2500.dp
                        } else {
                            left = right
                            right += 2500.dp
                        }
                    }
                }
            ) { i, item ->
                Box(
                    modifier = Modifier
                        .size(item.bounds.size)
                        .background(Color(Random.nextLong())),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = (i + 1).toString(), fontSize = 500.sp)
                }
            }
        }
    }
}
