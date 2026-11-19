package mobile.oats.lazytransformablelayout.tiledimage.extension

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import mobile.oats.lazytransformablelayout.LazyTransformableLayoutScope
import mobile.oats.lazytransformablelayout.LocalLazyTransformableLayoutState
import mobile.oats.lazytransformablelayout.tiledimage.DEFAULT_CACHE_BYTES
import mobile.oats.lazytransformablelayout.tiledimage.TileSource
import mobile.oats.lazytransformablelayout.tiledimage.TiledImageState
import mobile.oats.lazytransformablelayout.tiledimage.rememberTiledImageState
import mobile.oats.lazytransformablelayout.tiledimage.internal.TiledImage

/**
 * Adds a large raster to the layout as a single item. Only the tiles near the viewport are loaded, at a resolution
 * matching the zoom scale, and regions whose tiles are still loading show a lower resolution meanwhile.
 * Tiles are drawn edge to edge without overlapping, so translucent content blends correctly.
 *
 * @param source The raster to show
 * @param bounds Where the full raster is placed in the layout, defaulting to one dp per source pixel at the origin
 * @param zIndex The Z index of the image relative to other items in the layout
 * @param tileSize The width and height of each tile in decoded pixels
 * @param state Keeps tiles in memory after they leave the viewport. Pass one from [rememberTiledImageState] to keep
 * them while the image is out of composition. Otherwise the image keeps its own, which is dropped with it
 */
fun LazyTransformableLayoutScope.tiledImage(
    source: TileSource,
    bounds: DpRect = DpRect(0.dp, 0.dp, source.size.width.dp, source.size.height.dp),
    zIndex: Float = 0f,
    tileSize: Int = 512,
    state: TiledImageState? = null
) {
    item(bounds = bounds, zIndex = zIndex) {
        val layoutState = LocalLazyTransformableLayoutState.current
        val density = LocalDensity.current
        val ownState = remember(state) { state ?: TiledImageState(DEFAULT_CACHE_BYTES) }
        val image = remember(source, layoutState, bounds, density, tileSize, ownState) {
            TiledImage(source, layoutState, bounds, density, tileSize, ownState)
        }
        LaunchedEffect(image) { image.run() }

        Spacer(Modifier.fillMaxSize().drawBehind { with(image) { drawTiles() } })
    }
}
