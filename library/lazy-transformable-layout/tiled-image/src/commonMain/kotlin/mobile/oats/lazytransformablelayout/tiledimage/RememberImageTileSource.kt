package mobile.oats.lazytransformablelayout.tiledimage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.github.panpf.zoomimage.subsampling.ImageSource
import com.github.panpf.zoomimage.subsampling.toFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import mobile.oats.lazytransformablelayout.tiledimage.internal.ImageTileSource

/**
 * Prepares an encoded image, such as a JPEG, so its regions can be decoded without loading the whole image at once.
 * The decoder is released when this leaves composition or [imageSourceFactory] changes.
 *
 * @param imageSourceFactory Creates the encoded image, and may load it asynchronously, for example from a resource.
 * Keep it stable across recompositions, since a new, unequal factory prepares the image again
 * @return [ImageTileSourceState.Loading] until the image header is read, then either
 * [ImageTileSourceState.Ready] with a source to pass to [rememberTiledImageState] or [ImageTileSourceState.Failed]
 */
@Composable
fun rememberImageTileSource(imageSourceFactory: ImageSource.Factory): ImageTileSourceState {
    val state by produceState<ImageTileSourceState>(ImageTileSourceState.Loading, imageSourceFactory) {
        value = ImageTileSourceState.Loading
        val source = try {
            ImageTileSource.create(imageSourceFactory)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            value = ImageTileSourceState.Failed(e)
            return@produceState
        }

        value = ImageTileSourceState.Ready(source)
        try {
            awaitCancellation()
        } finally {
            source.close()
        }
    }
    return state
}

/**
 * Prepares an encoded image, such as a JPEG, so its regions can be decoded without loading the whole image at once.
 * The decoder is released when this leaves composition or [imageSource] changes.
 *
 * @param imageSource The encoded image, for example from `ImageSource.fromByteArray`. Remember it across
 * recompositions, since a new, unequal source prepares the image again
 * @return [ImageTileSourceState.Loading] until the image header is read, then either
 * [ImageTileSourceState.Ready] with a source to pass to [rememberTiledImageState] or [ImageTileSourceState.Failed]
 */
@Composable
fun rememberImageTileSource(imageSource: ImageSource) = rememberImageTileSource(imageSource.toFactory())
