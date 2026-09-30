package oats.mobile.lazytransformablelayout.demo.tiledwallpaper

import androidx.compose.ui.unit.DpRect
import com.github.panpf.zoomimage.util.IntRectCompat
import oats.mobile.lazytransformablelayout.model.Positionable

data class ImageTile(
    override val bounds: DpRect,
    override val zIndex: Float = 0f,
    val sourceRegion: IntRectCompat
) : Positionable
