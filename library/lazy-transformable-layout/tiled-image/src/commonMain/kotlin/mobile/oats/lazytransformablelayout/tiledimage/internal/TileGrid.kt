package mobile.oats.lazytransformablelayout.tiledimage.internal

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.floor
import kotlin.math.log2

private const val BLEED_DECODED_PX = 4

private const val LEVEL_HYSTERESIS = 1.15f

internal class TileGrid(
    private val id: Long,
    private val imageSize: IntSize,
    private val bounds: DpRect,
    private val tileSize: Int
) {
    val baseLevel = generateSequence(0) { it + 1 }
        .first { maxOf(imageSize.width, imageSize.height) shr it <= 2 * tileSize }

    fun levelFor(sourcePxPerScreenPx: Float, currentLevel: Int): Int {
        val staysInCurrentLevel = sourcePxPerScreenPx >= (1 shl currentLevel) / LEVEL_HYSTERESIS
            && sourcePxPerScreenPx <= (2 shl currentLevel) * LEVEL_HYSTERESIS

        return if (staysInCurrentLevel) currentLevel
        else floor(log2(sourcePxPerScreenPx)).toInt().coerceIn(0, baseLevel)
    }

    fun baseTile(zIndex: Float) = Tile(
        key = "$id:base",
        level = baseLevel,
        region = IntRect(0, 0, imageSize.width, imageSize.height),
        bounds = bounds,
        zIndex = zIndex
    )

    fun tiles(level: Int, zIndex: Float): List<Tile> {
        val sampleSize = 1 shl level
        val span = tileSize * sampleSize
        val bleed = BLEED_DECODED_PX * sampleSize
        val columns = (imageSize.width + span - 1) / span
        val rows = (imageSize.height + span - 1) / span

        return List(rows * columns) { index ->
            val row = index / columns
            val column = index % columns
            val region = IntRect(
                left = (column * span - bleed).coerceAtLeast(0),
                top = (row * span - bleed).coerceAtLeast(0),
                right = ((column + 1) * span + bleed).coerceAtMost(imageSize.width),
                bottom = ((row + 1) * span + bleed).coerceAtMost(imageSize.height)
            )
            val widthPerSourcePx = bounds.width / imageSize.width.toFloat()
            val heightPerSourcePx = bounds.height / imageSize.height.toFloat()

            Tile(
                key = "$id:$level:$column:$row",
                level = level,
                region = region,
                bounds = DpRect(
                    left = bounds.left + widthPerSourcePx * region.left.toFloat(),
                    top = bounds.top + heightPerSourcePx * region.top.toFloat(),
                    right = bounds.left + widthPerSourcePx * region.right.toFloat(),
                    bottom = bounds.top + heightPerSourcePx * region.bottom.toFloat()
                ),
                zIndex = zIndex
            )
        }
    }
}
