package mobile.oats.lazytransformablelayout.tiledimage

import androidx.compose.runtime.Stable
import mobile.oats.lazytransformablelayout.tiledimage.internal.CacheKey
import mobile.oats.lazytransformablelayout.tiledimage.internal.LoadedTile
import mobile.oats.lazytransformablelayout.tiledimage.internal.LruCache

/**
 * Keeps tiles of tiled images in memory after they leave the viewport, in case they are needed again.
 * Hoist it with [rememberTiledImageState] to keep the tiles while an image is out of composition,
 * or to share one memory budget between several images.
 */
@Stable
class TiledImageState internal constructor(cacheBytes: Long) {
    internal val cache = LruCache<CacheKey, LoadedTile>(cacheBytes) { it.bitmap.width * it.bitmap.height * 4L }
}
