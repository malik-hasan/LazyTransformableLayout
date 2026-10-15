package oats.mobile.lazytransformablelayout.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import oats.mobile.lazytransformablelayout.demo.quadrants.Quadrants
import oats.mobile.lazytransformablelayout.demo.thousandboxes.ThousandBoxes
import oats.mobile.lazytransformablelayout.demo.tiledwallpaper.TiledWallpaper

@Serializable
data object Home : NavKey

private val navConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Home::class)
            subclass(ThousandBoxes::class)
            subclass(Quadrants::class)
            subclass(TiledWallpaper::class)
        }
    }
}

@Composable
fun App() = MaterialTheme {
    val backStack = rememberNavBackStack(navConfiguration, Home)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Home> {
                Scaffold {
                    Column(
                        modifier = Modifier.padding(it).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button({ backStack += ThousandBoxes }) {
                            Text("Thousand Boxes")
                        }

                        Button({ backStack += Quadrants }) {
                            Text("Quadrants")
                        }

                        Button({ backStack += TiledWallpaper }) {
                            Text("Tiled Wallpaper")
                        }
                    }
                }
            }

            entry<ThousandBoxes> { ThousandBoxes() }
            entry<Quadrants> { Quadrants() }
            entry<TiledWallpaper> { TiledWallpaper() }
        }
    )
}
