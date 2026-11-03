package oats.mobile.lazytransformablelayout.internal

import androidx.compose.ui.input.pointer.PointerInputChange

internal sealed interface Press {
    class Tap(val pointerCount: Int) : Press
    class Drag(val change: PointerInputChange) : Press
}
