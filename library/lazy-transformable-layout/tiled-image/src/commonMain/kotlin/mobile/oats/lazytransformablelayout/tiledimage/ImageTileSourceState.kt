package mobile.oats.lazytransformablelayout.tiledimage

/**
 * The progress of preparing an encoded image for tiled display, returned by `rememberImageTileSource`.
 */
sealed interface ImageTileSourceState {

    /**
     * The image header is still being read.
     */
    data object Loading : ImageTileSourceState

    /**
     * The image is ready to show with [rememberTiledImageState].
     *
     * @property source The source to load the image's tiles from. It is closed when it leaves composition
     */
    data class Ready(val source: TileSource) : ImageTileSourceState

    /**
     * The image could not be read, for example because it is corrupt or its format is unsupported.
     *
     * @property error The reason the image could not be read
     */
    data class Failed(val error: Exception) : ImageTileSourceState
}
