package oats.mobile.lazytransformablelayout.demo.tiledwallpaper

import androidx.compose.ui.graphics.ImageBitmap
import com.github.panpf.zoomimage.compose.subsampling.asComposeBitmap
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.RegionDecoder
import com.github.panpf.zoomimage.subsampling.SubsamplingImage
import com.github.panpf.zoomimage.subsampling.fromByteArray
import com.github.panpf.zoomimage.subsampling.internal.defaultRegionDecoders
import com.github.panpf.zoomimage.util.IntRectCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class Wallpaper private constructor(private val decoder: RegionDecoder) {

    val imageInfo = decoder.getImageInfo()

    private val mutex = Mutex()

    suspend fun decodeTile(region: IntRectCompat, sampleSize: Int): ImageBitmap = withContext(Dispatchers.Default) {
        mutex.withLock {
            decoder.decodeRegion(region, sampleSize).asComposeBitmap()
        }
    }

    suspend fun close() = withContext(NonCancellable) {
        mutex.withLock { decoder.close() }
    }

    companion object {
        suspend fun create(bytes: ByteArray): Wallpaper {
            val imageSource = ImageSource.fromByteArray(bytes)
            val subsamplingImage = SubsamplingImage(imageSource)
            val decoder = defaultRegionDecoders()
                .first { it.accept(subsamplingImage) }
                .create(subsamplingImage, imageSource)
            return withContext(Dispatchers.Default) {
                decoder.prepare()
                Wallpaper(decoder)
            }
        }
    }
}
