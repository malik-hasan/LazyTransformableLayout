package mobile.oats.lazytransformablelayout.tiledimage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

internal const val DEFAULT_CACHE_BYTES = 64L * 1024 * 1024

/**
 * Creates a [TiledImageState] that keeps tiles in memory after they leave the viewport.
 * A new state, with an empty cache, is created whenever [cacheBytes] changes.
 *
 * @param cacheBytes The memory budget for tiles kept after they leave the viewport.
 * Tiles near the viewport are held in addition to it
 */
@Composable
fun rememberTiledImageState(cacheBytes: Long = DEFAULT_CACHE_BYTES) = remember(cacheBytes) {
    TiledImageState(cacheBytes)
}
