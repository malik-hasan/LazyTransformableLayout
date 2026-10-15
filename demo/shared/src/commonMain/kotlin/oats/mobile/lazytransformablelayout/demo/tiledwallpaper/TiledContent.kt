package oats.mobile.lazytransformablelayout.demo.tiledwallpaper

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

@Composable
internal fun TileContent(wallpaper: Wallpaper, tile: ImageTile) {
    // Only runs for tiles LazyTransformableLayout actually composes,
    // i.e. only for tiles inside the culling AABB. That's the thing we're testing.
    val bitmap by produceState<ImageBitmap?>(null, wallpaper, tile) {
        // Fixed sampleSize for now — swap for scale-driven sizing once this checks out.
        value = wallpaper.decodeTile(region = tile.sourceRegion, sampleSize = 2)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } ?: CircularProgressIndicator()
    }
}
