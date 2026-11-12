package mobile.oats.lazytransformablelayout.tiledimage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import mobile.oats.lazytransformablelayout.LazyTransformableLayoutState

/**
 * Creates a [TiledImageState] that shows [source] at a resolution matching the zoom scale of [layoutState].
 * A new state, with an empty cache, is created whenever any argument changes.
 *
 * The bounds are limited by the largest size a layout item can have. In pixels, either 65534 by 32766 or
 * 32766 by 65534 will always fit, and exceeding what `Constraints` can represent throws.
 *
 * @param source The raster to show
 * @param layoutState The state of the layout this image is placed in
 * @param bounds Where the full raster is placed in the layout, defaulting to one dp per source pixel at the origin
 * @param tileSize The width and height of each tile in decoded pixels
 * @param cacheBytes The memory budget for loaded tiles that are kept in case they are needed again
 */
@Composable
fun rememberTiledImageState(
    source: TileSource,
    layoutState: LazyTransformableLayoutState,
    bounds: DpRect = DpRect(0.dp, 0.dp, source.size.width.dp, source.size.height.dp),
    tileSize: Int = 512,
    cacheBytes: Long = 64L * 1024 * 1024
): TiledImageState {
    val density = LocalDensity.current
    val state = remember(source, layoutState, bounds, density, tileSize, cacheBytes) {
        TiledImageState(source, layoutState, bounds, density, tileSize, cacheBytes)
    }
    LaunchedEffect(state) { state.maintainLevels() }
    return state
}
