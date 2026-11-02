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

    val isUserInputBlocked
        get() = pendingAnimation != null && pendingMutatePriority == MutatePriority.PreventUserInput
            || animationJob?.isActive == true && animationMutatePriority == MutatePriority.PreventUserInput

    fun request(priority: MutatePriority, animation: suspend () -> Unit) {
        pendingMutatePriority = priority
        pendingAnimation = animation
    }

    suspend fun runPending() = coroutineScope {
        var previous: Job? = null
        snapshotFlow { pendingAnimation }
            .filterNotNull()
            .collect { pending ->
                pendingAnimation = null
                previous?.cancel()
                previous = launch(start = CoroutineStart.UNDISPATCHED) { pending() }
            }
    }

    suspend fun claim(
        priority: MutatePriority,
        block: suspend CoroutineScope.() -> Unit
    ) = coroutineScope {
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            val job = coroutineContext.job
            animationJob?.cancel()
            animationJob = job
            animationMutatePriority = priority
            pendingAnimation = null
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
}
