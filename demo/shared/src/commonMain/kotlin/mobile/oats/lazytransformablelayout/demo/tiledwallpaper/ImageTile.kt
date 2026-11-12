package mobile.oats.lazytransformablelayout.demo.tiledwallpaper

import androidx.compose.ui.unit.DpRect
import com.github.panpf.zoomimage.util.IntRectCompat
import mobile.oats.lazytransformablelayout.Positionable

data class ImageTile(
    val id: Int,
    override val bounds: DpRect,
    override val zIndex: Float = 0f,
    val sourceRegion: IntRectCompat
) : Positionable
