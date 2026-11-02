package oats.mobile.lazytransformablelayout.internal

import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent.Interval
import androidx.compose.runtime.Composable
import oats.mobile.lazytransformablelayout.Positionable

internal class Layer(
    val items: List<Positionable>,
    override val key: ((index: Int) -> Any)? = null,
    override val type: (index: Int) -> Any? = { null },
    val content: @Composable (Int) -> Unit
) : Interval
