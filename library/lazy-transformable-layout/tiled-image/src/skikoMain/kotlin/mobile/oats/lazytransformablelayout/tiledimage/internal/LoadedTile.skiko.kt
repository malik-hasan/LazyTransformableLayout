package mobile.oats.lazytransformablelayout.tiledimage.internal

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.IntOffset
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.SamplingMode

internal actual fun ImageBitmap.toShader(offset: IntOffset, scale: Int): Shader =
    Image.makeFromBitmap(asSkiaBitmap()).makeShader(
        FilterTileMode.CLAMP,
        FilterTileMode.CLAMP,
        SamplingMode.LINEAR,
        Matrix33.makeTranslate(offset.x.toFloat(), offset.y.toFloat())
            .makeConcat(Matrix33.makeScale(scale.toFloat()))
    ).asComposeShader()
