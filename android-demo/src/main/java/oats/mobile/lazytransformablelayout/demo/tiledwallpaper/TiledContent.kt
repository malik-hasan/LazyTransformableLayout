package oats.mobile.lazytransformablelayout.demo.tiledwallpaper

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.github.panpf.zoomimage.subsampling.internal.AndroidRegionDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TileContent(masterDecoder: AndroidRegionDecoder, tile: ImageTile) {
    var bitmap by remember(tile) { mutableStateOf<Bitmap?>(null) }

    // This LaunchedEffect only ever runs for tiles LazyTransformableLayout actually composes —
    // i.e. only for tiles inside the culling AABB. That's the thing we're testing.
    LaunchedEffect(tile) {
        withContext(Dispatchers.IO) {
            val tileDecoder = masterDecoder.copy() as AndroidRegionDecoder
            tileDecoder.use { tileDecoder ->
                // Fixed sampleSize for now — swap for scale-driven sizing once this checks out.
                bitmap = tileDecoder.decodeRegion(
                    region = tile.sourceRegion,
                    sampleSize = 2
                )
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
