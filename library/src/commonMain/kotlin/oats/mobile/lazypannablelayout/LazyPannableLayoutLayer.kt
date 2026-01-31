package oats.mobile.lazypannablelayout

import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent
import androidx.compose.runtime.Composable

internal data class LazyPannableLayoutLayer(
    val items: List<Positionable>,
    val content: @Composable (Int) -> Unit
) : LazyLayoutIntervalContent.Interval
