package `in`.dragonbra.javasteam.types

import `in`.dragonbra.javasteam.steam.steamclient.AsyncJobFailedException
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Note: await() is kotlin only.
// Note: Deferred is an alternative to C#'s Task<>; however, when a deferred is completed exceptionally,
// it's not considered cancelled. Also, there is no "isFaulted" state too.
class AsyncJobTest {

    internal class Callback(var isFinished: Boolean = false) : CallbackMsg()

    @Test
    fun asyncJobCompletesOnCallback() = runTest {
        val client = SteamClient()
        val asyncJob = AsyncJobSingle<Callback>(client, JobID(123))
        val asyncTask = asyncJob.asDeferred()

        client.postCallback(Callback().apply { jobID = JobID(123) })

        Assertions.assertTrue(asyncTask.isCompleted, "Async job should be completed after callback is posted")
        Assertions.assertFalse(asyncTask.isCancelled, "Async job should not be canceled after callback is posted")
    }

    @Test
    fun asyncJobGivesBackCallback() = runTest {
        val client = SteamClient()
        val asyncJob = AsyncJobSingle<Callback>(client, JobID(123))
        val asyncTask = asyncJob.asDeferred()

        val ourCallback = Callback().apply { jobID = JobID(123) }
        client.postCallback(ourCallback)

        Assertions.assertSame(asyncTask.await(), ourCallback)
    }

    @Test
    fun asyncJobCtorRegistersJob() {
        val client = SteamClient()
        val asyncJob = AsyncJobSingle<Callback>(client, JobID(123))

        Assertions.assertTrue(
            client.jobManager.asyncJobs.containsKey(asyncJob.jobID),
            "Async job dictionary should contain the jobid key"
        )

        Assertions.assertTrue(
            client.jobManager.asyncJobs.containsKey(JobID(123)),
            "Async job dictionary should contain jobid key as a value type"
        )
    }

    @Test
    fun asyncJobClearsOnCompletion() = runTest {
        val client = SteamClient()
        val asyncJob = AsyncJobSingle<Callback>(client, JobID(123))

        val callback = Callback().apply { jobID = JobID(123) }
        client.postCallback(callback)

        Assertions.assertFalse(
            client.jobManager.asyncJobs.containsKey(asyncJob.jobID),
            "Async job dictionary should no longer contain jobid key after callback is posted"
        )
        Assertions.assertFalse(
            client.jobManager.asyncJobs.containsKey(JobID(123)),
            "Async job dictionary should no longer contain jobid key (as value type) after callback is posted"
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun asyncJobClearsOnTimeout() = runTest {
        val client = SteamClient()

        val asyncJob = AsyncJobSingle<Callback>(client, JobID(123)).apply {
            timeout = 50.milliseconds
        }

        advanceTimeBy(70.milliseconds)
        client.jobManager.cancelPendingJobs()

        Assertions.assertFalse(
            client.jobManager.asyncJobs.containsKey(asyncJob.jobID),
            "Async job dictionary should no longer contain jobid key after timeout"
        )
        Assertions.assertFalse(
            client.jobManager.asyncJobs.containsKey(JobID(123)),
            "Async job dictionary should no longer contain jobid key (as value type) after timeout"
        )
    }

    @Test
    fun asyncJobCancelsOnSetFailedTimeout() = runTest {
        val client = SteamClient()
        val asyncJob = AsyncJobSingle<Callback>(client, JobID(123))

        val asyncTask = asyncJob.asDeferred()
        asyncJob.setFailed(false)

        Assertions.assertTrue(asyncTask.isCompleted, "Async job should be completed on message timeout")
        Assertions.assertTrue(asyncTask.isCancelled, "Async job should be canceled on message timeout")
        assertThrows<CancellationException> { asyncJob.await() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun asyncJobTimesOut() = runTest {
        val client = SteamClient()

        val asyncJob = AsyncJobSingle<Callback>(client, JobID(123)).apply {
            timeout = 50.milliseconds
        }
        val asyncTask = asyncJob.asDeferred()

        advanceTimeBy(70.milliseconds)
        client.jobManager.cancelPendingJobs()

        Assertions.assertTrue(asyncTask.isCompleted, "Async job should be completed yet")
        Assertions.assertTrue(asyncTask.isCancelled, "Async job should be canceled yet")
        assertThrows<CancellationException> { asyncTask.await() }
    }

    @Test
    fun asyncJobThrowsFailureExceptionOnFailure() = runTest {
        val client = SteamClient()

        val asyncJob = AsyncJobSingle<Callback>(client, JobID(123))
        val asyncTask = asyncJob.asDeferred()

        asyncJob.setFailed(true)

        Assertions.assertTrue(asyncTask.isCompleted, "Async job should be completed after job failure")
        Assertions.assertFalse(asyncTask.isCancelled, "Async job should not be canceled after job failure")
        assertThrows<AsyncJobFailedException> { asyncTask.await() }
    }

    @Test
    fun asyncJobMultipleFinishedOnEmptyPredicate() = runTest {
        val client = SteamClient()

        val asyncJob = AsyncJobMultiple<Callback>(client, JobID(123)) { _ -> true }
        val asyncTask = asyncJob.asDeferred()

        val jobFinished = asyncJob.addResult(Callback().apply { jobID = JobID(123) })

        Assertions.assertTrue(
            jobFinished,
            "Async job should inform that it is completed when completion predicate is always true and a result is given"
        )
        Assertions.assertTrue(
            asyncTask.isCompleted,
            "Async job should be completed when empty predicate result is given"
        )
        Assertions.assertFalse(
            asyncTask.isCancelled,
            "Async job should not be canceled when empty predicate result is given"
        )
    }

    @Test
    fun asyncJobMultipleFinishedOnPredicate() = runTest {
        val client = SteamClient()

        val asyncJob = AsyncJobMultiple<Callback>(client, JobID(123)) { call -> call.isFinished }
        val asyncTask = asyncJob.asDeferred()

        var jobFinished = asyncJob.addResult(
            Callback().apply {
                jobID = JobID(123)
                isFinished = false
            }
        )

        Assertions.assertFalse(
            jobFinished,
            "Async job should not inform that it is finished when completion predicate is false after a result is given"
        )
        Assertions.assertFalse(
            asyncTask.isCompleted,
            "Async job should not be completed when completion predicate is false"
        )
        Assertions.assertFalse(
            asyncTask.isCancelled,
            "Async job should not be canceled when completion predicate is false"
        )

        jobFinished = asyncJob.addResult(
            Callback().apply {
                jobID = JobID(123)
                isFinished = true
            }
        )

        Assertions.assertTrue(
            jobFinished,
            "Async job should inform completion when completion predicate is passed after a result is given"
        )
        Assertions.assertTrue(
            asyncTask.isCompleted,
            "Async job should be completed when completion predicate is true"
        )
        Assertions.assertFalse(
            asyncTask.isCancelled,
            "Async job should not be canceled when completion predicate is true"
        )
    }

    @Test
    fun asyncJobMultipleClearsOnCompletion() = runTest {
        val client = SteamClient()

        val asyncJob = AsyncJobMultiple<Callback>(client, JobID(123)) { call -> call.isFinished }

        client.postCallback(
            Callback().apply {
                jobID = JobID(123)
                isFinished = true
            }
        )

        Assertions.assertFalse(
            client.jobManager.asyncJobs.containsKey(asyncJob.jobID),
            "Async job dictionary should not contain jobid key for AsyncJobMultiple on completion"
        )
        Assertions.assertFalse(
            client.jobManager.asyncJobs.containsKey(JobID(123)),
            "Async job dictionary should not contain jobid key (as value type) for AsyncJobMultiple on completion"
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun asyncJobMultipleClearsOnTimeout() = runTest {
        val client = SteamClient()

        val asyncJob = AsyncJobMultiple<Callback>(client, JobID(123)) { _ -> true }
        asyncJob.timeout = 50.milliseconds

        advanceTimeBy(70.milliseconds)
        client.jobManager.cancelPendingJobs()

        Assertions.assertFalse(
            client.jobManager.asyncJobs.containsKey(asyncJob.jobID),
            "Async job dictionary should no longer contain jobid key after timeout"
        )
        Assertions.assertFalse(
            client.jobManager.asyncJobs.containsKey(JobID(123)),
            "Async job dictionary should no longer contain jobid key (as value type) after timeout"
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun asyncJobMultipleExtendsTimeoutOnMessage() = runTest {
        val client = SteamClient()

        val asyncJob = AsyncJobMultiple<Callback>(client, JobID(123)) { call -> call.isFinished }
        asyncJob.timeout = 50.milliseconds

        val asyncTask = asyncJob.asDeferred()

        // we should not be completed or canceled yet
        Assertions.assertFalse(asyncTask.isCompleted, "AsyncJobMultiple should not be completed yet")
        Assertions.assertFalse(asyncTask.isCancelled, "AsyncJobMultiple should not be canceled yet")

        // give result 1 of 2
        asyncJob.addResult(
            Callback().apply {
                jobID = JobID(123)
                isFinished = false
            }
        )

        // delay for what the original timeout would have been
        advanceTimeBy(70.milliseconds)

        client.jobManager.cancelPendingJobs()

        // we still shouldn't be completed or canceled (timed out)
        Assertions.assertFalse(
            asyncTask.isCompleted,
            "AsyncJobMultiple should not be completed yet after result was added (result should extend timeout)"
        )
        Assertions.assertFalse(
            asyncTask.isCancelled,
            "AsyncJobMultiple should not be canceled yet after result was added (result should extend timeout)"
        )

        asyncJob.addResult(
            Callback().apply {
                jobID = JobID(123)
                isFinished = true
            }
        )

        // we should be completed but not canceled or faulted
        Assertions.assertTrue(
            asyncTask.isCompleted,
            "AsyncJobMultiple should be completed when final result is added to set"
        )
        Assertions.assertFalse(
            asyncTask.isCancelled,
            "AsyncJobMultiple should not be canceled when final result is added to set"
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun asyncJobMultipleTimesOut() = runTest {
        val client = SteamClient()

        val asyncJob = AsyncJobMultiple<Callback>(client, JobID(123)) { _ -> false }.apply {
            timeout = 50.milliseconds
        }

        val asyncTask = asyncJob.asDeferred()

        advanceTimeBy(70.milliseconds)
        client.jobManager.cancelPendingJobs()

        Assertions.assertTrue(
            asyncTask.isCompleted,
            "AsyncJobMultiple should be completed after 5 seconds of a 1 second job timeout"
        )
        Assertions.assertTrue(
            asyncTask.isCancelled,
            "AsyncJobMultiple should be canceled after 5 seconds of a 1 second job timeout"
        )
        assertThrows<CancellationException> { asyncTask.await() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun asyncJobMultipleCompletesOnIncompleteResult() = runTest {
        val client = SteamClient()

        val asyncJob = AsyncJobMultiple<Callback>(client, JobID(123)) { call -> call.isFinished }.apply {
            timeout = 1.seconds
        }

        val asyncTask = asyncJob.asDeferred()

        val onlyResult = Callback().apply {
            jobID = JobID(123)
            isFinished = false
        }

        asyncJob.addResult(onlyResult)

        // adding a result will extend the job's timeout, but we'll cheat here and decrease it
        asyncJob.timeout = 50.milliseconds

        advanceTimeBy(70.milliseconds)
        client.jobManager.cancelPendingJobs()

        Assertions.assertTrue(
            asyncTask.isCompleted,
            "AsyncJobMultiple should be completed on partial (timed out) result set"
        )
        Assertions.assertFalse(
            asyncTask.isCancelled,
            "AsyncJobMultiple should not be canceled on partial (timed out) result set"
        )

        val result = asyncTask.await()

        Assertions.assertFalse(result.complete, "ResultSet should be incomplete")
        Assertions.assertFalse(result.failed, "ResultSet should not be failed")
        Assertions.assertTrue(result.results.size == 1)
        Assertions.assertSame(onlyResult, result.results.first())
    }

    @Test
    fun asyncJobMultipleCompletesOnIncompleteResultAndFailure() = runTest {
        val client = SteamClient()

        val asyncJob = AsyncJobMultiple<Callback>(client, JobID(123)) { call -> call.isFinished }.apply {
            timeout = 1.seconds
        }

        val asyncTask = asyncJob.asDeferred()

        val onlyResult = Callback().apply {
            jobID = JobID(123)
            isFinished = false
        }

        asyncJob.addResult(onlyResult)

        asyncJob.setFailed(true)

        Assertions.assertTrue(
            asyncTask.isCompleted,
            "AsyncJobMultiple should be completed on partial (failed) result set"
        )
        Assertions.assertFalse(
            asyncTask.isCancelled,
            "AsyncJobMultiple should not be canceled on partial (failed) result set"
        )

        val result = asyncTask.await()

        Assertions.assertFalse(result.complete, "ResultSet should be incomplete")
        Assertions.assertTrue(result.failed, "ResultSet should be failed")
        Assertions.assertTrue(result.results.size == 1)
        Assertions.assertSame(onlyResult, result.results.first())
    }

    @Test
    fun asyncJobMultipleThrowsFailureExceptionOnFailure() = runTest {
        val client = SteamClient()

        val asyncJob = AsyncJobMultiple<Callback>(client, JobID(123)) { _ -> false }
        val asyncTask = asyncJob.asDeferred()

        asyncJob.setFailed(true)

        Assertions.assertTrue(asyncTask.isCompleted, "AsyncJobMultiple should be completed after job failure")
        Assertions.assertFalse(asyncTask.isCancelled, "AsyncJobMultiple should not be canceled after job failure")
        assertThrows<AsyncJobFailedException> { asyncTask.await() }
    }

//    @Test
//    fun asyncJobContinuesAsynchronously()

//    @Test
//    fun AsyncJobMultipleContinuesAsynchronously()
}
