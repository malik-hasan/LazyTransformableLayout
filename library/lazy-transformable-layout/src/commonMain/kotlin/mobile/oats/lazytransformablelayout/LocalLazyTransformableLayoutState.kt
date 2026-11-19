package mobile.oats.lazytransformablelayout

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The state of the [LazyTransformableLayout] an item is in, provided to the content of every item
 */
val LocalLazyTransformableLayoutState = staticCompositionLocalOf<LazyTransformableLayoutState> {
    error("LocalLazyTransformableLayoutState is only provided to items of a LazyTransformableLayout")
}
