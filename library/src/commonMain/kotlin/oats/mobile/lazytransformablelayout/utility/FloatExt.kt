package oats.mobile.lazytransformablelayout.utility

import kotlin.math.PI

val Float.radians
    get() = this * PI.toFloat() / 180f

val Float.degrees
    get() = this * 180f / PI.toFloat()
