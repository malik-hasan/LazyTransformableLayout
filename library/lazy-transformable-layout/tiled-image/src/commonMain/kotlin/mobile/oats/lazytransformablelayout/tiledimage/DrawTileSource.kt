package mobile.oats.lazytransformablelayout.tiledimage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Creates a [TileSource] that renders each region by running [draw], for large canvases
 * and for bitmaps that are already fully loaded.
 *
 * To show new content, create a new source and pass it to [rememberTiledImageState].
 *
 * @param size The size of the full drawing in source pixels, which is also the [DrawScope] size seen by [draw]
 * @param dispatcher The dispatcher [draw] runs on
 * @param draw Draws the full raster. It is clipped to the requested region, and may run concurrently with itself
 */
fun DrawTileSource(
    size: IntSize,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    draw: DrawScope.() -> Unit
): TileSource = object : TileSource {
    override val size = size

    override suspend fun tile(region: IntRect, sampleSize: Int) = withContext(dispatcher) {
        val bitmap = ImageBitmap(
            width = (region.width + sampleSize - 1) / sampleSize,
            height = (region.height + sampleSize - 1) / sampleSize
        )
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size.toSize()) {
            val scale = 1f / sampleSize
            scale(scale, scale, Offset.Zero) {
                translate(-region.left.toFloat(), -region.top.toFloat(), draw)
            }
        }
        bitmap
    }
}
