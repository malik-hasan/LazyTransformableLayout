package oats.mobile.lazytransformablelayout.model

import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent
import androidx.compose.runtime.Composable

internal data class LazyTransformableLayoutLayer(
    val items: List<Positionable>,
    val content: @Composable (Int) -> Unit
) : LazyLayoutIntervalContent.Interval
