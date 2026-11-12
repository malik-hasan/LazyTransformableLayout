package mobile.oats.lazytransformablelayout.demo.tiledwallpaper

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.fromByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mobile.oats.lazytransformablelayout.LazyTransformableLayout
import mobile.oats.lazytransformablelayout.demo.resources.Res
import mobile.oats.lazytransformablelayout.demo.resources.home
import mobile.oats.lazytransformablelayout.rememberLazyTransformableLayoutState
import mobile.oats.lazytransformablelayout.tiledimage.ImageTileSourceState
import mobile.oats.lazytransformablelayout.tiledimage.TileSource
import mobile.oats.lazytransformablelayout.tiledimage.extension.tiledImage
import mobile.oats.lazytransformablelayout.tiledimage.rememberImageTileSource
import mobile.oats.lazytransformablelayout.tiledimage.rememberTiledImageState
import org.jetbrains.compose.resources.painterResource

@Serializable
@SerialName("tiled-wallpaper")
data object TiledWallpaper : NavKey

private data object WallpaperImageSource : ImageSource.Factory {
    override val key = "files/hireswallpaper.jpeg"

    override suspend fun create() = ImageSource.fromByteArray(Res.readBytes(key))
}

@Composable
fun TiledWallpaper() {
    when (val state = rememberImageTileSource(WallpaperImageSource)) {
        ImageTileSourceState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is ImageTileSourceState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Failed: ${state.error.message}")
        }

        is ImageTileSourceState.Ready -> TiledWallpaper(state.source)
    }
}

@Composable
private fun TiledWallpaper(source: TileSource) {
    val layoutState = rememberLazyTransformableLayoutState(
        DpRect(0.dp, 0.dp, source.size.width.dp, source.size.height.dp)
    )
    val image = rememberTiledImageState(source, layoutState)

    Scaffold { scaffoldPadding ->
        LazyTransformableLayout(
            state = layoutState,
            modifier = Modifier.padding(scaffoldPadding)
        ) {
            tiledImage(image)
        }

        IconButton({ layoutState.snapTo(rotationAngle = 0f) }) {
            Icon(painterResource(Res.drawable.home), "Home")
        }
    }
}
