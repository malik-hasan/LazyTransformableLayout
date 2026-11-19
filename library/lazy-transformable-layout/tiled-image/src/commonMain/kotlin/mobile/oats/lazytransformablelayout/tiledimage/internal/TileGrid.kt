package mobile.oats.lazytransformablelayout.tiledimage.internal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2

private const val BLEED_DECODED_PX = 4

private const val LEVEL_HYSTERESIS = 1.15f

internal class TileGrid(
    private val imageSize: IntSize,
    private val tileSize: Int
) {
    val baseLevel = generateSequence(0) { it + 1 }
        .first { maxOf(imageSize.width, imageSize.height) shr it <= 2 * tileSize }

    val baseTile = IntRect(IntOffset.Zero, imageSize).let { Tile(baseLevel, 0, 0, it, it) }

    fun levelFor(sourcePxPerScreenPx: Float, currentLevel: Int): Int {
        val staysInCurrentLevel = sourcePxPerScreenPx >= (1 shl currentLevel) / LEVEL_HYSTERESIS
            && sourcePxPerScreenPx <= (2 shl currentLevel) * LEVEL_HYSTERESIS

        return if (staysInCurrentLevel) currentLevel
        else floor(log2(sourcePxPerScreenPx)).toInt().coerceIn(0, baseLevel)
    }

    fun spanOf(level: Int) = tileSize shl level

    fun tile(level: Int, column: Int, row: Int): Tile {
        val span = spanOf(level)
        val bleed = BLEED_DECODED_PX shl level
        val core = IntRect(
            left = column * span,
            top = row * span,
            right = minOf((column + 1) * span, imageSize.width),
            bottom = minOf((row + 1) * span, imageSize.height)
        )
        return Tile(
            level = level,
            column = column,
            row = row,
            core = core,
            decoded = IntRect(
                left = maxOf(core.left - bleed, 0),
                top = maxOf(core.top - bleed, 0),
                right = minOf(core.right + bleed, imageSize.width),
                bottom = minOf(core.bottom + bleed, imageSize.height)
            )
        )
    }

    fun tilesIn(level: Int, region: Rect): List<Tile> {
        val span = spanOf(level)
        val firstColumn = floor(region.left / span).toInt().coerceAtLeast(0)
        val firstRow = floor(region.top / span).toInt().coerceAtLeast(0)
        val lastColumn = ceil(region.right / span).toInt().coerceAtMost((imageSize.width + span - 1) / span) - 1
        val lastRow = ceil(region.bottom / span).toInt().coerceAtMost((imageSize.height + span - 1) / span) - 1

        return buildList {
            for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) add(tile(level, column, row))
        }
    }

    fun ancestorOf(tile: Tile, level: Int): Tile {
        val shift = level - tile.level
        return if (level >= baseLevel) baseTile else tile(level, tile.column shr shift, tile.row shr shift)
    }

    fun childrenOf(tile: Tile) = buildList {
        for (row in 2 * tile.row..2 * tile.row + 1) for (column in 2 * tile.column..2 * tile.column + 1) {
            val child = tile(tile.level - 1, column, row)
            if (!child.core.isEmpty) add(child)
        }
    }
}
