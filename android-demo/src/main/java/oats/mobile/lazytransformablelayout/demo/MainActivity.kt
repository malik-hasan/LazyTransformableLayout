package oats.mobile.lazytransformablelayout.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import oats.mobile.lazytransformablelayout.LazyTransformableLayout
import oats.mobile.lazytransformablelayout.LazyTransformableLayoutState
import oats.mobile.lazytransformablelayout.model.Positionable
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val density = LocalDensity.current

            val boxes = remember {
                mutableStateListOf<Positionable>(
                    *(1..1000).map {
                        object : Positionable {
                            override val bounds = DpRect(
                                DpOffset(
                                    Random.nextInt(0, 10000).dp,
                                    Random.nextInt(0, 10000).dp
                                ),
                                DpSize(48.dp, 48.dp),
                            )
                        }
                    }.toTypedArray()
                )
            }

            Scaffold { scaffoldPadding ->
                LazyTransformableLayout(
                    modifier = Modifier.padding(scaffoldPadding),
                    state = remember {
                        LazyTransformableLayoutState(
                            layoutBounds = DpRect(
                                origin = DpOffset(0.dp, 0.dp),
                                size = DpSize(10000.dp, 10000.dp)
                            ),
                            density = density
                        )
                    }
                ) {
                    item(DpRect(DpOffset(5.dp, 5.dp), DpSize(96.dp, 48.dp))) {
                        var color by remember { mutableStateOf(Color.Red) }
                        Button(
                            modifier = Modifier.sizeIn(maxWidth = 130.dp, maxHeight = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            onClick = {
                                boxes += object : Positionable {
                                    override val bounds = DpRect(
                                        DpOffset(
                                            Random.nextInt(0, 10000).dp,
                                            Random.nextInt(0, 10000).dp
                                        ),
                                        DpSize(48.dp, 48.dp),
                                    )
                                }
                                color = if (color == Color.Red) {
                                    Color.DarkGray
                                } else Color.Red
                            }
                        ) {
                            Text("Add box")
                        }
                    }

                    item(DpRect(DpOffset(105.dp, 5.dp), DpSize(96.dp, 48.dp))) {
                        Button(
                            modifier = Modifier.sizeIn(maxWidth = 130.dp, maxHeight = 48.dp),
                            onClick = { boxes.removeAt(0) }
                        ) {
                            Text("Remove box")
                        }
                    }

                    items(boxes) {
                        Box(Modifier.size(30.dp).background(Color.Blue))
                    }
                }
            }
        }
    }
}
