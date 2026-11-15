package mobile.oats.lazytransformablelayout.tiledimage.internal

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import mobile.oats.lazytransformablelayout.LazyTransformableLayoutState
import mobile.oats.lazytransformablelayout.tiledimage.TileSource
import mobile.oats.lazytransformablelayout.tiledimage.TiledImageState

internal class TiledImage(
    private val source: TileSource,
    private val layoutState: LazyTransformableLayoutState,
    private val bounds: DpRect,
    tileSize: Int,
    state: TiledImageState
) {
    init {
        require(source.size.width > 0 && source.size.height > 0) { "The source must not be empty" }
        require(tileSize > 0) { "tileSize must be positive" }
    }

    private val grid = TileGrid(source.size, tileSize)

    private val loaded = mutableStateMapOf<Tile, LoadedTile>()

    private var base by mutableStateOf<LoadedTile?>(null)

    private val failed = mutableSetOf<Tile>()

    private val dpPerSourcePxX = bounds.width / source.size.width

    private val dpPerSourcePxY = bounds.height / source.size.height

    private var level by mutableIntStateOf(grid.baseLevel)

    private val wantedTiles by derivedStateOf(structuralEqualityPolicy()) {
        val level = level
        val visibleArea = layoutState.visibleLayoutArea
        if (level >= grid.baseLevel || visibleArea == null) return@derivedStateOf emptySet()

        val span = grid.spanOf(level).toFloat()
        val corners = visibleArea.corners
        val region = Rect(
            left = (corners.minOf { it.x } - bounds.left) / dpPerSourcePxX,
            top = (corners.minOf { it.y } - bounds.top) / dpPerSourcePxY,
            right = (corners.maxOf { it.x } - bounds.left) / dpPerSourcePxX,
            bottom = (corners.maxOf { it.y } - bounds.top) / dpPerSourcePxY
        ).inflate(span)

        grid.tilesIn(level, region).filterTo(HashSet()) {
            visibleArea.intersects(layoutBoundsOf(it.core, span))
        }
    }

    private val imagePath = Path().apply {
        addRect(Rect(0f, 0f, source.size.width.toFloat(), source.size.height.toFloat()))
    }

    private val tilePaint = Paint().apply {
        isAntiAlias = false
        filterQuality = FilterQuality.Low
    }

    private fun layoutBoundsOf(region: IntRect, margin: Float) = DpRect(
        left = bounds.left + dpPerSourcePxX * (region.left - margin),
        top = bounds.top + dpPerSourcePxY * (region.top - margin),
        right = bounds.left + dpPerSourcePxX * (region.right + margin),
        bottom = bounds.top + dpPerSourcePxY * (region.bottom + margin)
    )

    private val sourcePxPerScreenPx: Float?
        get() {
            val topLeft = layoutState.layoutToViewport(DpOffset(bounds.left, bounds.top))
            if (!topLeft.isSpecified) return null
            val topRight = layoutState.layoutToViewport(DpOffset(bounds.right, bounds.top))
            val screenPxPerSourcePx = (topRight - topLeft).getDistance() / source.size.width
            return 1 / (screenPxPerSourcePx * maxOf(1f, dpPerSourcePxY / dpPerSourcePxX))
        }

    private val cache = state.cache

    private fun cacheKey(tile: Tile) = CacheKey(source, tile)

    suspend fun run(): Nothing = try {
        coroutineScope {
            launch {
                base = cache.remove(cacheKey(grid.baseTile)) ?: load(grid.baseTile)
            }

            launch {
                snapshotFlow { sourcePxPerScreenPx }.filterNotNull().collect {
                    level = grid.levelFor(it, level)
                }
            }

            loadWantedTiles()
        }
    } finally {
        loaded.forEach { (tile, loadedTile) -> cache[cacheKey(tile)] = loadedTile }
        loaded.clear()
        base?.let { cache[cacheKey(grid.baseTile)] = it }
        base = null
    }

    private suspend fun loadWantedTiles(): Nothing = coroutineScope {
        val jobs = HashMap<Tile, Job>()
        snapshotFlow { wantedTiles }.collect { wanted ->
            jobs.keys.filterNot { it in wanted }.forEach { jobs.remove(it)?.cancel() }
            loaded.keys.filterNot { it in wanted }.forEach { tile ->
                loaded.remove(tile)?.let { cache[cacheKey(tile)] = it }
            }

            val center = layoutState.visibleLayoutArea?.corners?.run {
                (this[0].x + this[2].x) / 2 to (this[0].y + this[2].y) / 2
            }
            wanted.filter { it !in loaded && it !in jobs && it !in failed }
                .sortedBy { tile ->
                    center?.let { (x, y) ->
                        val tileBounds = layoutBoundsOf(tile.core, 0f)
                        val dx = ((tileBounds.left + tileBounds.right) / 2 - x).value
                        val dy = ((tileBounds.top + tileBounds.bottom) / 2 - y).value
                        dx * dx + dy * dy
                    } ?: 0f
                }.forEach { tile ->
                    val cached = cache.remove(cacheKey(tile))
                    if (cached != null) loaded[tile] = cached
                    else {
                        val job = launch(start = CoroutineStart.LAZY) { load(tile)?.let { loaded[tile] = it } }
                        job.invokeOnCompletion { if (jobs[tile] === job) jobs.remove(tile) }
                        jobs[tile] = job
                        job.start()
                    }
                }
        }
        error("snapshotFlow never completes")
    }

    private suspend fun load(tile: Tile) = try {
        LoadedTile(tile, source.tile(tile.decoded, 1 shl tile.level))
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Logger.w(e) { "Failed to load tile $tile" }
        failed += tile
        null
    }

    fun DrawScope.drawTiles() = drawIntoCanvas { canvas ->
        canvas.save()
        canvas.scale(bounds.width.toPx() / source.size.width, bounds.height.toPx() / source.size.height)
        canvas.clipPath(imagePath)

        val wanted = wantedTiles
        if (wanted.isEmpty()) base?.let { canvas.fill(it, grid.baseTile.core) }
        else wanted.forEach { tile ->
            val own = loaded[tile] ?: cache.peek(cacheKey(tile))
            if (own != null) {
                canvas.fill(own, tile.core)
                return@forEach
            }

            val children = if (tile.level > 0) grid.childrenOf(tile) else emptyList()
            val loadedChildren = children.map { cache.peek(cacheKey(it)) }
            if (loadedChildren.any { it != null }) children.forEachIndexed { i, child ->
                loadedChildren[i]?.let { canvas.fill(it, child.core) } ?: canvas.fillFromAncestor(tile, child.core)
            } else canvas.fillFromAncestor(tile, tile.core)
        }

        canvas.restore()
    }

    private fun Canvas.fillFromAncestor(tile: Tile, region: IntRect) {
        for (ancestorLevel in tile.level + 1 until grid.baseLevel) {
            val ancestor = grid.ancestorOf(tile, ancestorLevel)
            (loaded[ancestor] ?: cache.peek(cacheKey(ancestor)))?.let {
                fill(it, region)
                return
            }
        }
        base?.let { fill(it, region) }
    }

    private fun Canvas.fill(tile: LoadedTile, region: IntRect) {
        val width = source.size.width.toFloat()
        val height = source.size.height.toFloat()
        tilePaint.shader = tile.shader
        drawRect(
            Rect(
                left = if (region.left == 0) -width else region.left.toFloat(),
                top = if (region.top == 0) -height else region.top.toFloat(),
                right = if (region.right == source.size.width) 2 * width else region.right.toFloat(),
                bottom = if (region.bottom == source.size.height) 2 * height else region.bottom.toFloat()
            ),
            tilePaint
        )
    }
}
