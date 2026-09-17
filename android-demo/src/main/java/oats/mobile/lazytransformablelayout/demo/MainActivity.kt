package oats.mobile.lazytransformablelayout.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import oats.mobile.lazytransformablelayout.demo.quadrants.Quadrants
import oats.mobile.lazytransformablelayout.demo.tenthousandboxes.TenThousandBoxes
import oats.mobile.lazytransformablelayout.demo.tiledwallpaper.TiledWallpaper

@Serializable
data object Home : NavKey

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val backstack = rememberNavBackStack(Home)
            NavDisplay(
                backStack = backstack,
                entryProvider = entryProvider {
                    entry<Home> {
                        Scaffold {
                            Column(
                                modifier = Modifier.padding(it).fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button({ backstack += TenThousandBoxes }) {
                                    Text("Ten Thousand Boxes")
                                }

                                Button({ backstack += Quadrants }) {
                                    Text("Quadrants")
                                }

                                Button({ backstack += TiledWallpaper }) {
                                    Text("Tiled Wallpaper")
                                }
                            }
                        }
                    }

                    entry<TenThousandBoxes> { TenThousandBoxes() }
                    entry<Quadrants> { Quadrants() }
                    entry<TiledWallpaper> { TiledWallpaper() }
                }
            )
        }
    }
}
