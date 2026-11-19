package mobile.oats.lazytransformablelayout.extension

import androidx.compose.animation.core.FloatDecayAnimationSpec

internal fun FloatDecayAnimationSpec.durationNanos(velocity: Float) =
    getDurationNanos(0f, velocity)

internal fun FloatDecayAnimationSpec.valueAt(playTimeNanos: Long, velocity: Float) =
    getValueFromNanos(playTimeNanos, 0f, velocity)
