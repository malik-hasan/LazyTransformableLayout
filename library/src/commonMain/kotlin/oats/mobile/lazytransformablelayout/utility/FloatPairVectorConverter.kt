package oats.mobile.lazytransformablelayout.utility

import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter

internal val FloatPairVectorConverter = TwoWayConverter<Pair<Float, Float>, AnimationVector2D>(
    convertToVector = { AnimationVector2D(it.first, it.second) },
    convertFromVector = { it.v1 to it.v2 }
)
