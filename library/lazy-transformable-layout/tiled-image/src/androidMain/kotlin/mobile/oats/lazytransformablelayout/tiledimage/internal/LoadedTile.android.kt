package mobile.oats.lazytransformablelayout.tiledimage.internal

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader.TileMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntOffset

internal actual fun ImageBitmap.toShader(offset: IntOffset, scale: Int): Shader =
    BitmapShader(asAndroidBitmap(), TileMode.CLAMP, TileMode.CLAMP).apply {
        setLocalMatrix(
            Matrix().apply {
                setScale(scale.toFloat(), scale.toFloat())
                postTranslate(offset.x.toFloat(), offset.y.toFloat())
            }
        )
    }
