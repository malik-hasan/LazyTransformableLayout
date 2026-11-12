package mobile.oats.lazytransformablelayout.tiledimage.extension

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import mobile.oats.lazytransformablelayout.LazyTransformableLayoutScope
import mobile.oats.lazytransformablelayout.tiledimage.TiledImageState
import mobile.oats.lazytransformablelayout.tiledimage.internal.Tile
import mobile.oats.lazytransformablelayout.tiledimage.rememberTiledImageState

/**
 * Adds a large raster to the layout, split into tiles that load only while near the viewport and at
 * a resolution matching the zoom scale. Adjacent tiles overlap so no seams appear between them.
 *
 * The overlap assumes opaque content: translucent pixels blend twice along tile edges.
 *
 * @param state The state created by [rememberTiledImageState]
 * @param zIndex The Z index of the image relative to other items in the layout
 */
fun LazyTransformableLayoutScope.tiledImage(state: TiledImageState, zIndex: Float = 0f) {
    val grid = state.grid
    val level = state.level
    val tiles = buildList {
        add(grid.baseTile(zIndex))
        state.previousLevel?.takeIf { it != grid.baseLevel }?.let { addAll(grid.tiles(it, zIndex)) }
        if (level != grid.baseLevel) addAll(grid.tiles(level, zIndex))
    }

    items(tiles, key = { it.key }) { Tile(state, it) }
}

@Composable
private fun Tile(state: TiledImageState, tile: Tile) {
    val decoding = tile.level == state.grid.baseLevel || tile.level == state.level
    var failed by remember(tile.key) { mutableStateOf(false) }

    val bitmap by produceState<ImageBitmap?>(state.cache[tile.key], tile.key, decoding) {
        if (value == null && decoding) try {
            value = state.source.tile(tile.region, 1 shl tile.level).also { state.cache[tile.key] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "Failed to load tile ${tile.key}" }
            failed = true
        }
    }

    val loading = bitmap == null && decoding && !failed
    DisposableEffect(loading) {
        if (loading) state.pendingTiles++
        onDispose { if (loading) state.pendingTiles-- }
    }

    Spacer(
        Modifier.fillMaxSize().drawBehind {
            bitmap?.let {
                val sampleSize = 1 shl tile.level
                scale(
                    scaleX = sampleSize * state.layoutPxPerSourcePxX,
                    scaleY = sampleSize * state.layoutPxPerSourcePxY,
                    pivot = Offset.Zero
                ) {
                    drawImage(it, filterQuality = FilterQuality.Low)
                }
            }
        }
    )
}
