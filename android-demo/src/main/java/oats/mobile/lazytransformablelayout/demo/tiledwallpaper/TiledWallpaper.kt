package oats.mobile.lazytransformablelayout.demo.tiledwallpaper
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.fromAsset
import com.github.panpf.zoomimage.subsampling.internal.AndroidRegionDecoder
import com.github.panpf.zoomimage.util.IntRectCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import oats.mobile.lazytransformablelayout.LazyTransformableLayout
import oats.mobile.lazytransformablelayout.LazyTransformableLayoutState
import oats.mobile.lazytransformablelayout.model.Positionable

@Serializable
data object TiledWallpaper : NavKey

private const val TILE_GRID = 4 // 4x4 = 16 tiles

private data class ImageTile(
    override val bounds: DpRect,
    override val zIndex: Float = 0f,
    val sourceRegion: IntRectCompat
) : Positionable

@Composable
fun TiledWallpaper() {
    val context = LocalContext.current
    val density = LocalDensity.current

    // One prepared decoder as the source of truth for image info; each tile gets its own
    // copy() for thread-safe concurrent decoding (AndroidRegionDecoder is explicitly
    // documented as not thread-safe).
    var masterDecoder by remember { mutableStateOf<AndroidRegionDecoder?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val decoder = AndroidRegionDecoder(
                    imageSource = ImageSource.fromAsset(context, "hireswallpaper.jpeg")
                )
                decoder.prepare()
                decoder.getImageInfo() // forces info to be read/cached now, not on first tile
                masterDecoder = decoder
            } catch (e: Exception) {
                loadError = e.message
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { masterDecoder?.close() }
    }

    val decoder = masterDecoder
    if (loadError != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Failed: $loadError")
        }
        return
    }
    if (decoder == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val info = decoder.getImageInfo()

    // Treating 1 source pixel = 1 dp for this test's coordinate space, matching how the
    // library's own demo builds layoutBounds — not a claim about physical screen density.
    val layoutState = remember(info) {
        LazyTransformableLayoutState(
            layoutBounds = Rect(
                offset = Offset.Zero,
                size = with(density) { Size(info.width.dp.toPx(), info.height.dp.toPx()) }
            )
        )
    }

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
                    add(
                        ImageTile(
                            bounds = DpRect(left.dp, top.dp, right.dp, bottom.dp),
                            sourceRegion = IntRectCompat(left, top, right, bottom)
                        )
                    )
                }
            }
        }
    }

    LazyTransformableLayout(
        state = layoutState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(tiles) { _, tile ->
            TileContent(masterDecoder = decoder, tile = tile)
        }
    }
}

@Composable
private fun TileContent(masterDecoder: AndroidRegionDecoder, tile: ImageTile) {
    var bitmap by remember(tile) { mutableStateOf<Bitmap?>(null) }

    // This LaunchedEffect only ever runs for tiles LazyTransformableLayout actually composes —
    // i.e. only for tiles inside the culling AABB. That's the thing we're testing.
    LaunchedEffect(tile) {
        withContext(Dispatchers.IO) {
            val tileDecoder = masterDecoder.copy() as AndroidRegionDecoder
            try {
                // Fixed sampleSize for now — swap for scale-driven sizing once this checks out.
                bitmap = tileDecoder.decodeRegion(
                    region = tile.sourceRegion,
                    sampleSize = 2
                )
            } finally {
                tileDecoder.close()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
