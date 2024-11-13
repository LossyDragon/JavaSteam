package `in`.dragonbra.javasteam.steam.steamclient

import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.AsyncJob
import `in`.dragonbra.javasteam.types.JobID
import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.time.Duration.Companion.seconds

/**
 * @author Lossy
 * @since 2023-03-17
 */
class AsyncJobManager {

    private val asyncJobs = ConcurrentHashMap<JobID, AsyncJob>()

    private var jobTimeoutJob: Job? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        setTimeoutsEnabled(true)
    }

    /**
     * Tracks a job with this manager.
     */
    fun startJob(asyncJob: AsyncJob) {
        asyncJobs[asyncJob.jobID] = asyncJob
    }

    /**
     * Passes a callback to a pending async job.
     * If the given callback completes the job, the job is removed from this manager.
     */
    suspend fun tryCompleteJob(jobId: JobID, callback: CallbackMsg) {
        val asyncJob = getJob(jobId) ?: return

        // pass this callback into the job so it can determine if the job is finished
        val jobFinished = asyncJob.addResult(callback)

        if (jobFinished) {
            // if the job is finished, we can stop tracking it
            asyncJobs.remove(jobId)
        }
    }

    /**
     * Extends the lifetime of a job.
     */
    fun heartbeatJob(jobId: JobID) {
        val asyncJob = getJob(jobId) ?: return
        asyncJob.heartbeat()
    }

    /**
     * Marks a certain job as remotely failed.
     */
    suspend fun failJob(jobId: JobID) {
        val asyncJob = getJob(jobId, andRemove = true) ?: return
        asyncJob.setFailed(dueToRemoteFailure = true)
    }

    /**
     * Cancels and clears all jobs being tracked.
     */
    suspend fun cancelPendingJobs() {
        asyncJobs.values.forEach { asyncJob ->
            asyncJob.setFailed(dueToRemoteFailure = false)
        }
        asyncJobs.clear()
    }

    /**
     * Enables or disables periodic checks for job timeouts.
     */
    fun setTimeoutsEnabled(enable: Boolean) {
        if (enable) {
            startTimeoutChecker()
        } else {
            stopTimeoutChecker()
        }
    }

    private fun startTimeoutChecker() {
        jobTimeoutJob?.cancel()
        jobTimeoutJob = coroutineScope.launch {
            while (isActive) {
                cancelTimedoutJobs()
                delay(1.seconds)
            }
        }
    }

    private fun stopTimeoutChecker() {
        jobTimeoutJob?.cancel()
        jobTimeoutJob = null
    }

    /**
     * This is called periodically to cancel and clear out any jobs that have timed out.
     */
    private suspend fun cancelTimedoutJobs() {
        asyncJobs.values.toList().forEach { job ->
            if (job.isTimedOut) {
                job.setFailed(dueToRemoteFailure = false)
                asyncJobs.remove(job.jobID)
            }
        }
    }

    /**
     * Retrieves a job from this manager, and optionally removes it from tracking.
     */
    private fun getJob(jobId: JobID, andRemove: Boolean = false): AsyncJob? {
        return if (andRemove) {
            asyncJobs.remove(jobId)
        } else {
            asyncJobs[jobId]
        }
    }

    /**
     * Cleanup resources when the manager is no longer needed
     */
    fun shutdown() {
        coroutineScope.cancel()
    }
}
