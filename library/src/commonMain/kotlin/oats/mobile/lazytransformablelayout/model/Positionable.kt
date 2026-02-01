package oats.mobile.lazytransformablelayout.model

import androidx.compose.ui.unit.DpRect

/**
 * A Positionable item in a LazyTransformableLayout
 *
 * @property bounds A DpRect specifying the position and maximum bounds for this item.
 *
 * The left and top bounds represent the X, Y coordinates of the Composable.
 *
 * The right and bottom bounds represent the maxWidth and maxHeight of the Composable.
 * This value should match what is passed to the Composable using modifiers such as `.width()` or `.widthIn(max=)`.
 * ***The lazy optimization can only be as effective as the right/bottom bounds provided are accurate.***
 *
 * @property zIndex The Z index of this item
 */
interface Positionable {
    val bounds: DpRect
    val zIndex: Float get() = 0f
}
