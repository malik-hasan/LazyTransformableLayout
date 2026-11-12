package mobile.oats.lazytransformablelayout.tiledimage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

/**
 * A large raster that can be loaded one region at a time, such as a region-decodable image file or a tile server.
 *
 * The caller of a source owns its lifecycle, including closing any underlying decoder.
 *
 * @property size The size of the full raster in source pixels
 */
interface TileSource {
    val size: IntSize

    /**
     * Loads a region of the raster. Called concurrently for different regions and from the main thread,
     * so implementations must move blocking work to another dispatcher and limit their own parallelism.
     *
     * @param region The region to load in source pixels, always within [size]
     * @param sampleSize A power of two. The returned bitmap holds one pixel per [sampleSize] source pixels in each axis
     * @return A bitmap of the region, sized `region.size / sampleSize`, rounded either way
     */
    suspend fun tile(region: IntRect, sampleSize: Int): ImageBitmap
}
