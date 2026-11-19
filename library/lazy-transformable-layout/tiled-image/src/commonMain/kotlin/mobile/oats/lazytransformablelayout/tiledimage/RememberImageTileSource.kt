package mobile.oats.lazytransformablelayout.tiledimage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import com.github.panpf.zoomimage.subsampling.ImageSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import mobile.oats.lazytransformablelayout.tiledimage.internal.ImageTileSource

/**
 * Prepares an encoded image, such as a JPEG, so its regions can be decoded without loading the whole image at once.
 * The decoder is released when this leaves composition or the key of [imageSourceFactory] changes.
 *
 * @param imageSourceFactory Creates the encoded image, and may load it asynchronously, for example from a resource.
 * Only a different [ImageSource.Factory.key] prepares the image again, so a new factory instance with the same key
 * is ignored
 * @return [ImageTileSourceState.Loading] until the image header is read, then either
 * [ImageTileSourceState.Ready] with a source to pass to `tiledImage` or [ImageTileSourceState.Failed]
 */
@Composable
fun rememberImageTileSource(imageSourceFactory: ImageSource.Factory): ImageTileSourceState {
    val currentFactory by rememberUpdatedState(imageSourceFactory)
    return produceState<ImageTileSourceState>(ImageTileSourceState.Loading, imageSourceFactory.key) {
        try {
            value = ImageTileSourceState.Loading
            val source = ImageTileSource.create(currentFactory)
            try {
                value = ImageTileSourceState.Ready(source)
                awaitCancellation()
            } finally {
                source.close()
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            value = ImageTileSourceState.Failed(e)
        }
    }.value
}
