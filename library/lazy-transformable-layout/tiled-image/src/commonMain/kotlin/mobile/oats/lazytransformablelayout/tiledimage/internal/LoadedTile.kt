package mobile.oats.lazytransformablelayout.tiledimage.internal

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.unit.IntOffset
import mobile.oats.lazytransformablelayout.tiledimage.TileSource

internal class LoadedTile(tile: Tile, val bitmap: ImageBitmap) {
    val shader = bitmap.toShader(tile.decoded.topLeft, 1 shl tile.level)
}

internal data class CacheKey(val source: TileSource, val tile: Tile)

internal expect fun ImageBitmap.toShader(offset: IntOffset, scale: Int): Shader
