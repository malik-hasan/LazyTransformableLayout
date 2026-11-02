package oats.mobile.lazytransformablelayout

import androidx.compose.foundation.MutatePriority
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import oats.mobile.lazytransformablelayout.model.PendingAnimation

internal class AnimationCoordinator {
    private var pendingAnimation by mutableStateOf<PendingAnimation?>(null)

    private var animationJob: Job? = null
    private var animationMutatePriority = MutatePriority.Default

    fun request(priority: MutatePriority, animation: suspend () -> Unit) {
        if (pendingAnimation?.canBeOverriddenBy(priority) != false) {
            pendingAnimation = PendingAnimation(priority, animation)
        }
    }

    suspend fun runPending() = coroutineScope {
        var previous: Job? = null
        var previousPriority = MutatePriority.Default
        snapshotFlow { pendingAnimation }
            .filterNotNull()
            .collect { pending ->
                pendingAnimation = null
                val pendingPriority = pending.priority
                if (previous?.isActive != true || pendingPriority >= previousPriority) {
                    previous?.cancel()
                    previous = launch(start = CoroutineStart.UNDISPATCHED) {
                        pending.animation()
                    }
                    previousPriority = pendingPriority
                }
            }
    }

    suspend fun claim(
        priority: MutatePriority,
        block: suspend CoroutineScope.() -> Unit
    ) {
        if (canClaim(priority)) {
            coroutineScope {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    val job = coroutineContext.job
                    animationJob?.cancel()
                    animationJob = job
                    animationMutatePriority = priority
                    if (pendingAnimation?.canBeOverriddenBy(priority) == true) pendingAnimation = null
                    try {
                        coroutineScope { block() }
                    } finally {
                        if (animationJob == job) animationJob = null
                    }
                }
            }
        }
    }

    private fun canClaim(priority: MutatePriority) =
        animationJob?.isActive != true || priority >= animationMutatePriority

    fun canCancel(priority: MutatePriority) =
        canClaim(priority) && pendingAnimation?.canBeOverriddenBy(priority) != false

    fun tryCancel(priority: MutatePriority) = canCancel(priority).also {
        if (it) {
            animationJob?.cancel()
            pendingAnimation = null
        }
    }
}
