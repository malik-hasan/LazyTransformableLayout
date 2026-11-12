package mobile.oats.lazytransformablelayout.internal

import kotlinx.coroutines.CancellationException
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import androidx.compose.foundation.MutatePriority
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AnimationCoordinatorTest {

    @Test
    fun `given a higher priority claim is running, when a lower priority claim starts, should not run it`() = runTest {
        val coordinator = AnimationCoordinator()
        val release = CompletableDeferred<Unit>()
        val log = mutableListOf<String>()

        launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.claim(MutatePriority.PreventUserInput) {
                log += "high"
                release.await()
            }
        }
        coordinator.claim(MutatePriority.Default) { log += "low" }
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("high"), log)
    }

    @Test
    fun `given a lower priority claim is running, when a higher priority claim starts, should cancel the lower one`() = runTest {
        val coordinator = AnimationCoordinator()
        val log = mutableListOf<String>()

        val low = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.claim(MutatePriority.Default) {
                try {
                    CompletableDeferred<Unit>().await()
                } finally {
                    log += "low cancelled"
                }
            }
        }
        coordinator.claim(MutatePriority.UserInput) { log += "high" }
        advanceUntilIdle()

        assertEquals(setOf("low cancelled", "high"), log.toSet())
        low.cancel()
    }

    private fun runPendingAndCollect(
        configure: AnimationCoordinator.(log: MutableList<String>) -> Unit
    ): List<String> {
        val log = mutableListOf<String>()
        val coordinator = AnimationCoordinator()
        coordinator.configure(log)
        runTest {
            val clock = BroadcastFrameClock()
            val job = launch(clock) { coordinator.runPending() }
            advanceUntilIdle()
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()
            job.cancel()
        }
        return log
    }

    @Test
    fun `given a pending request, when a lower priority request arrives, should drop it`() {
        val log = runPendingAndCollect { log ->
            request(MutatePriority.UserInput) { log += "high" }
            request(MutatePriority.Default) { log += "low" }
        }

        assertEquals(listOf("high"), log)
    }

    @Test
    fun `given a pending request, when a higher priority request arrives, should replace the pending one`() {
        val log = runPendingAndCollect { log ->
            request(MutatePriority.Default) { log += "low" }
            request(MutatePriority.UserInput) { log += "high" }
        }

        assertEquals(listOf("high"), log)
    }

    @Test
    fun `given a pending request, when an equal priority request arrives, should replace the pending one`() {
        val log = runPendingAndCollect { log ->
            request(MutatePriority.Default) { log += "first" }
            request(MutatePriority.Default) { log += "second" }
        }

        assertEquals(listOf("second"), log)
    }

    @Test
    fun `given a higher priority claim is running, when trying to cancel with a lower priority, should not cancel it`() = runTest {
        val coordinator = AnimationCoordinator()
        val release = CompletableDeferred<Unit>()
        var cancelled = false
        launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.claim(MutatePriority.PreventUserInput) {
                try {
                    release.await()
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }
            }
        }

        assertFalse(coordinator.canCancel(MutatePriority.Default))
        assertFalse(coordinator.tryCancel(MutatePriority.Default))
        advanceUntilIdle()
        assertFalse(cancelled)
        release.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `given a lower priority claim is running, when trying to cancel with a higher priority, should cancel it`() = runTest {
        val coordinator = AnimationCoordinator()
        var cancelled = false
        launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.claim(MutatePriority.Default) {
                try {
                    CompletableDeferred<Unit>().await()
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }
            }
        }

        assertTrue(coordinator.tryCancel(MutatePriority.UserInput))
        advanceUntilIdle()
        assertTrue(cancelled)
    }

    @Test
    fun `given a higher priority request is pending, when trying to cancel with a lower priority, should keep the request`() {
        val log = runPendingAndCollect { log ->
            request(MutatePriority.PreventUserInput) { log += "high" }
            assertFalse(tryCancel(MutatePriority.Default))
        }

        assertEquals(listOf("high"), log)
    }

    @Test
    fun `given a lower priority request is pending, when trying to cancel with a higher priority, should discard the request`() {
        val log = runPendingAndCollect { log ->
            request(MutatePriority.Default) { log += "low" }
            assertTrue(tryCancel(MutatePriority.UserInput))
        }

        assertEquals(emptyList(), log)
    }

    @Test
    fun `given a lower priority request is pending, when a higher priority claim starts, should discard the request`() {
        val log = runPendingAndCollect { log ->
            request(MutatePriority.Default) { log += "low" }
            runTest { claim(MutatePriority.UserInput) { log += "claim" } }
        }

        assertEquals(listOf("claim"), log)
    }

    @Test
    fun `given a higher priority animation is running, when a lower priority request is pending, should not run it`() = runTest {
        val coordinator = AnimationCoordinator()
        val log = mutableListOf<String>()
        val clock = BroadcastFrameClock()
        val job = launch(clock) { coordinator.runPending() }
        advanceUntilIdle()

        coordinator.request(MutatePriority.PreventUserInput) { log += "high"; CompletableDeferred<Unit>().await() }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        coordinator.request(MutatePriority.Default) { log += "low" }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals(listOf("high"), log)
        job.cancel()
    }

    @Test
    fun `given a running animation, when an equal priority request is pending, should cancel the running one and run the request`() = runTest {
        val coordinator = AnimationCoordinator()
        val log = mutableListOf<String>()
        val clock = BroadcastFrameClock()
        val job = launch(clock) { coordinator.runPending() }
        advanceUntilIdle()

        coordinator.request(MutatePriority.Default) {
            try {
                CompletableDeferred<Unit>().await()
            } finally {
                log += "first cancelled"
            }
        }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        coordinator.request(MutatePriority.Default) { log += "second" }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals(setOf("first cancelled", "second"), log.toSet())
        assertEquals(2, log.size)
        job.cancel()
    }

    @Test
    fun `given a claim is running, when another claim of equal priority starts, should cancel the running one and run the new one`() = runTest {
        val coordinator = AnimationCoordinator()
        val log = mutableListOf<String>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.claim(MutatePriority.Default) {
                try {
                    CompletableDeferred<Unit>().await()
                } finally {
                    log += "first cancelled"
                }
            }
        }

        coordinator.claim(MutatePriority.Default) { log += "second" }
        advanceUntilIdle()

        assertEquals(setOf("first cancelled", "second"), log.toSet())
        first.cancel()
    }
}
