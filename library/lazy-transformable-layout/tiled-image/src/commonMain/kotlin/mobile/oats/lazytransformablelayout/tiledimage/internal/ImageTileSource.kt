package mobile.oats.lazytransformablelayout.tiledimage.internal

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.github.panpf.zoomimage.compose.subsampling.asComposeBitmap
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.SubsamplingImage
import com.github.panpf.zoomimage.subsampling.internal.TileDecoder
import com.github.panpf.zoomimage.subsampling.internal.defaultRegionDecoders
import com.github.panpf.zoomimage.util.IntRectCompat
import com.github.panpf.zoomimage.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import mobile.oats.lazytransformablelayout.tiledimage.TileSource

private const val MAX_PARALLEL_DECODES = 3

internal class ImageTileSource private constructor(private val decoder: TileDecoder) : TileSource {

    override val size = decoder.imageInfo.run { IntSize(width, height) }

    private val decodeDispatcher = Dispatchers.Default.limitedParallelism(MAX_PARALLEL_DECODES)

    override suspend fun tile(region: IntRect, sampleSize: Int): ImageBitmap = withContext(decodeDispatcher) {
        checkNotNull(
            decoder.decode(
                IntRectCompat(region.left, region.top, region.right, region.bottom),
                sampleSize
            )
        ) { "No bitmap decoded for $region" }.asComposeBitmap()
    }

    suspend fun close() = withContext(NonCancellable + decodeDispatcher) { decoder.close() }

    companion object {
        suspend fun create(imageSourceFactory: ImageSource.Factory) = withContext(Dispatchers.Default) {
            val imageSource = imageSourceFactory.create()
            val subsamplingImage = SubsamplingImage(imageSource)
            val regionDecoder = defaultRegionDecoders()
                .first { it.accept(subsamplingImage) }
                .create(subsamplingImage, imageSource)
            regionDecoder.prepare()
            ImageTileSource(TileDecoder(Logger(tag = "ImageTileSource"), regionDecoder))
        }
    }
}
