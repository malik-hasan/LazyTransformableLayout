package mobile.oats.lazytransformablelayout.extension

import kotlin.math.PI

val Float.radians
    get() = this * PI.toFloat() / 180f
