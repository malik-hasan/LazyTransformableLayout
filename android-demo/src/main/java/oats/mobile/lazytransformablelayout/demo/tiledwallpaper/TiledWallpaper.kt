package oats.mobile.lazytransformablelayout.demo.tiledwallpaper

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.fromAsset
import com.github.panpf.zoomimage.subsampling.internal.AndroidRegionDecoder
import com.github.panpf.zoomimage.util.IntRectCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import oats.mobile.lazytransformablelayout.LazyTransformableLayout
import oats.mobile.lazytransformablelayout.demo.R
import oats.mobile.lazytransformablelayout.rememberLazyTransformableLayoutState

@Serializable
data object TiledWallpaper : NavKey

private const val TILE_GRID = 4 // 4x4 = 16 tiles
private const val BLEED_PX = 8 // Must be larger than your max sampleSize

@Composable
fun TiledWallpaper() {
    val context = LocalContext.current
    val density = LocalDensity.current

    var decoder by remember { mutableStateOf<AndroidRegionDecoder?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                decoder = AndroidRegionDecoder(
                    ImageSource.fromAsset(context, "hireswallpaper.jpeg")
                ).also { it.prepare() }
            } catch (e: Exception) {
                loadError = e.message
            }
        }

        onDispose { decoder?.close() }
    }

    if (decoder == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val info = decoder!!.getImageInfo()

    val layoutState = rememberLazyTransformableLayoutState(
        Rect(
            offset = Offset.Zero,
            size = with(density) { Size(info.width.dp.toPx(), info.height.dp.toPx()) }
        )
    )

    val tileWidthPx = info.width / TILE_GRID
    val tileHeightPx = info.height / TILE_GRID

    val tiles = remember(info) {
        buildList {
            for (row in 0 until TILE_GRID) {
                for (col in 0 until TILE_GRID) {
                    val left = col * tileWidthPx
                    val top = row * tileHeightPx
                    val right = if (col == TILE_GRID - 1) info.width else left + tileWidthPx
                    val bottom = if (row == TILE_GRID - 1) info.height else top + tileHeightPx

                    // Only bleed on internal edges (don't exceed image boundaries)
                    val bleedRight = if (col == TILE_GRID - 1) 0 else BLEED_PX
                    val bleedBottom = if (row == TILE_GRID - 1) 0 else BLEED_PX

                    add(
                        ImageTile(
                            // 1 source px = 1 dp, so bleed 8.dp in layout space
                            bounds = DpRect(
                                left = left.dp,
                                top = top.dp,
                                right = (right + bleedRight).dp,
                                bottom = (bottom + bleedBottom).dp
                            ),
                            // Bleed 8 source pixels in region space
                            sourceRegion = IntRectCompat(
                                left = left,
                                top = top,
                                right = right + bleedRight,
                                bottom = bottom + bleedBottom
                            )
                        )
                    )
                }
            }
        }
    }

    Scaffold { scaffoldPadding ->
        LazyTransformableLayout(
            state = layoutState,
            modifier = Modifier.padding(scaffoldPadding)
        ) {
            loadError?.let {
                item(DpRect(DpOffset.Zero, DpSize(150.dp, 50.dp))) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Failed: $loadError")
                    }
                }
            } ?: itemsIndexed(tiles) { _, tile ->
                TileContent(masterDecoder = decoder!!, tile = tile)
            }
        }

        IconButton({ layoutState.set(angle = 0f) }) {
            Icon(painterResource(R.drawable.home), "Home")
        }
    }
}
