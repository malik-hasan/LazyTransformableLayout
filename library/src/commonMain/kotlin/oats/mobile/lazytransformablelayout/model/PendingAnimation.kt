package oats.mobile.lazytransformablelayout.model

import androidx.compose.foundation.MutatePriority

internal class PendingAnimation(
    val priority: MutatePriority,
    val animation: suspend () -> Unit
) {
    fun canBeOverriddenBy(otherPriority: MutatePriority) = otherPriority >= priority
}
