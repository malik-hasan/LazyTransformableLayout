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

internal class AnimationCoordinator {
    private var pendingAnimation by mutableStateOf<(suspend () -> Unit)?>(null)
    private var pendingMutatePriority = MutatePriority.Default

    private var animationJob: Job? = null
    private var animationMutatePriority = MutatePriority.Default

    fun request(priority: MutatePriority, animation: suspend () -> Unit) {
        if (pendingAnimation == null || priority >= pendingMutatePriority) {
            pendingMutatePriority = priority
            pendingAnimation = animation
        }
    }

    suspend fun runPending() = coroutineScope {
        var previous: Job? = null
        var previousPriority = MutatePriority.Default
        snapshotFlow { pendingAnimation }
            .filterNotNull()
            .collect { pending ->
                pendingAnimation = null
                val priority = pendingMutatePriority
                if (previous?.isActive != true || priority >= previousPriority) {
                    previous?.cancel()
                    previous = launch(start = CoroutineStart.UNDISPATCHED) {
                        pending()
                    }
                    previousPriority = priority
                }
            }
    }

    suspend fun claim(
        priority: MutatePriority,
        block: suspend CoroutineScope.() -> Unit
    ) = canClaim(priority) && coroutineScope {
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            val job = coroutineContext.job
            animationJob?.cancel()
            animationJob = job
            animationMutatePriority = priority
            if (pendingAnimation != null && priority >= pendingMutatePriority) pendingAnimation = null
            try {
                coroutineScope { block() }
            } finally {
                if (animationJob == job) animationJob = null
            }
        }
        job.join()
        !job.isCancelled
    }

    fun cancel() {
        animationJob?.cancel()
        pendingAnimation = null
    }

    private fun canClaim(priority: MutatePriority) =
        animationJob?.isActive != true || priority >= animationMutatePriority

    fun canCancel(priority: MutatePriority) =
        canClaim(priority) && (pendingAnimation == null || priority >= pendingMutatePriority)

    fun tryCancel(priority: MutatePriority) = canCancel(priority).also {
        if (it) cancel()
    }
}
