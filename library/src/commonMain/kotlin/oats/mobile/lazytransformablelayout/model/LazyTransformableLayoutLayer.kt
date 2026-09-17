package oats.mobile.lazytransformablelayout.model

import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent.Interval
import androidx.compose.runtime.Composable

internal data class LazyTransformableLayoutLayer(
    val items: List<Positionable>,
    val content: @Composable (Int) -> Unit
) : Interval
