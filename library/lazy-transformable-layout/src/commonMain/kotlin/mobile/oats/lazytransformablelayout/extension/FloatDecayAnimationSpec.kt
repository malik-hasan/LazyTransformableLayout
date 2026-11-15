package mobile.oats.lazytransformablelayout.extension

import androidx.compose.animation.core.FloatDecayAnimationSpec

internal fun FloatDecayAnimationSpec.durationNanos(velocity: Float) = if (velocity == 0f) {
    0L
} else getDurationNanos(0f, velocity)

internal fun FloatDecayAnimationSpec.valueAt(
    playTimeNanos: Long,
    velocity: Float
) = if (velocity == 0f) 0f else getValueFromNanos(playTimeNanos, 0f, velocity)
