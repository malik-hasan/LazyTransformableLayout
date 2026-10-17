package oats.mobile.lazytransformablelayout.demo.tiledwallpaper

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.github.panpf.zoomimage.util.IntRectCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.Serializable
import oats.mobile.lazytransformablelayout.LazyTransformableLayout
import oats.mobile.lazytransformablelayout.demo.resources.Res
import oats.mobile.lazytransformablelayout.demo.resources.home
import oats.mobile.lazytransformablelayout.rememberLazyTransformableLayoutState
import org.jetbrains.compose.resources.painterResource

@Serializable
data object TiledWallpaper : NavKey

private const val TILE_GRID = 4 // 4x4 = 16 tiles
private const val BLEED_PX = 8 // Must be larger than your max sampleSize

private sealed interface WallpaperState {
    data object Loading : WallpaperState
    data class Loaded(val wallpaper: Wallpaper) : WallpaperState
    data class Failed(val message: String?) : WallpaperState
}

@Composable
fun TiledWallpaper() {
    val wallpaperState by produceState<WallpaperState>(WallpaperState.Loading) {
        val wallpaper = try {
            Wallpaper.create(Res.readBytes("files/hireswallpaper.jpeg"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            value = WallpaperState.Failed(e.message)
            return@produceState
        }

        value = WallpaperState.Loaded(wallpaper)
        try {
            awaitCancellation()
        } finally {
            wallpaper.close()
        }
    }

    when (val state = wallpaperState) {
        WallpaperState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is WallpaperState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Failed: ${state.message}")
        }

        is WallpaperState.Loaded -> TiledWallpaper(state.wallpaper)
    }
}

@Composable
private fun TiledWallpaper(wallpaper: Wallpaper) {
    val info = wallpaper.imageInfo

    val layoutState = rememberLazyTransformableLayoutState(
        DpRect(0.dp, 0.dp, info.width.dp, info.height.dp)
    )

    val tiles = remember(info) {
        val tileWidthPx = info.width / TILE_GRID
        val tileHeightPx = info.height / TILE_GRID

        buildList {
            for (row in 0 until TILE_GRID) {
                for (col in 0 until TILE_GRID) {
                    val left = col * tileWidthPx
                    val top = row * tileHeightPx
                    val right = if (col == TILE_GRID - 1) info.width else left + tileWidthPx
                    val bottom = if (row == TILE_GRID - 1) info.height else top + tileHeightPx

                    // Only bleed on internal edges (don't exceed image boundaries)
                    val bleedRight = if (col == TILE_GRID - 1) 0 else BLEED_PX
                    val bleedBottom = if (row == TILE_GRID - 1) 0 else BLEED_PX

                    add(
                        ImageTile(
                            id = row * TILE_GRID + col,
                            // 1 source px = 1 dp, so bleed 8.dp in layout space
                            bounds = DpRect(
                                left = left.dp,
                                top = top.dp,
                                right = (right + bleedRight).dp,
                                bottom = (bottom + bleedBottom).dp
                            ),
                            // Bleed 8 source pixels in region space
                            sourceRegion = IntRectCompat(
                                left = left,
                                top = top,
                                right = right + bleedRight,
                                bottom = bottom + bleedBottom
                            )
                        )
                    )
                }
            }
        }
    }

    Scaffold { scaffoldPadding ->
        LazyTransformableLayout(
            state = layoutState,
            modifier = Modifier.padding(scaffoldPadding)
        ) {
            items(tiles, key = { it.id }) { tile ->
                TileContent(wallpaper = wallpaper, tile = tile)
            }
        }

        IconButton({ layoutState.set(angle = 0f) }) {
            Icon(painterResource(Res.drawable.home), "Home")
        }
    }
}
