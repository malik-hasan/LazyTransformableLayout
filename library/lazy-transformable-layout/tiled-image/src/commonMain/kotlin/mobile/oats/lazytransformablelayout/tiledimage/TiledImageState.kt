package mobile.oats.lazytransformablelayout.tiledimage

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mobile.oats.lazytransformablelayout.LazyTransformableLayoutState
import mobile.oats.lazytransformablelayout.tiledimage.internal.LruCache
import mobile.oats.lazytransformablelayout.tiledimage.internal.TileGrid

private var nextStateId = 0L

/**
 * The state of a tiled image, which chooses the resolution to show from the zoom scale of a layout
 * and keeps recently loaded tiles in memory. Create one with [rememberTiledImageState].
 */
@Stable
class TiledImageState internal constructor(
    internal val source: TileSource,
    private val layoutState: LazyTransformableLayoutState,
    bounds: DpRect,
    density: Density,
    tileSize: Int,
    cacheBytes: Long
) {
    init {
        require(source.size.width > 0 && source.size.height > 0) { "The source must not be empty" }
        require(tileSize > 0) { "tileSize must be positive" }
    }

    internal val grid = TileGrid(nextStateId++, source.size, bounds, tileSize)

    internal val cache = LruCache<String, ImageBitmap>(cacheBytes) { it.width * it.height * 4L }

    internal val layoutPxPerSourcePxX = with(density) { bounds.width.toPx() } / source.size.width

    internal val layoutPxPerSourcePxY = with(density) { bounds.height.toPx() } / source.size.height

    private val sharpestLayoutPxPerSourcePx = maxOf(layoutPxPerSourcePxX, layoutPxPerSourcePxY)

    internal var level by mutableIntStateOf(
        grid.levelFor(1 / (sharpestLayoutPxPerSourcePx * layoutState.zoomScale), grid.baseLevel)
    )
        private set

    internal var previousLevel by mutableStateOf<Int?>(null)
        private set

    internal var pendingTiles by mutableIntStateOf(0)

    internal suspend fun maintainLevels() = coroutineScope {
        launch {
            snapshotFlow { layoutState.zoomScale }.collect { zoomScale ->
                val nextLevel = grid.levelFor(1 / (sharpestLayoutPxPerSourcePx * zoomScale), level)
                if (nextLevel != level) {
                    previousLevel = level
                    level = nextLevel
                }
            }
        }

        snapshotFlow { previousLevel }.collectLatest { previous ->
            if (previous != null) {
                // Tiles of the new level are only composed by the frame after the level changes
                repeat(2) { withFrameNanos { } }
                snapshotFlow { pendingTiles }.first { it == 0 }
                previousLevel = null
            }
        }
    }
}
