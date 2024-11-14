package `in`.dragonbra.javasteam.types

import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for coroutine-based async jobs.
 * Should not be used directly, but rather with [AsyncJob] or [AsyncJobMultiple].
 * @author Lossy
 * @since 2023-03-17
 */
abstract class AsyncJob(
    protected val client: SteamClient,
    val jobID: JobID,
) {
    var timeout: Duration = 10.seconds

    private val jobStart = System.nanoTime()

    val isTimedOut: Boolean
        get() = (System.nanoTime() - jobStart) / 1_000_000_000.0 >= timeout.inWholeSeconds

    init {
        registerJob()
    }

    private fun registerJob() {
        client.startJob(this)
    }

    /**
     * Adds a callback to the async job's result set.
     * @return true if this result completes the set; otherwise, false.
     */
    internal abstract fun addResult(callback: CallbackMsg): Boolean

    /**
     * Sets this job as failed, either remotely or due to a message timeout.
     */
    internal abstract fun setFailed(dueToRemoteFailure: Boolean)

    /**
     * Marks this job as having received a heartbeat and extends the job's timeout.
     */
    internal fun heartbeat() {
        timeout += 10.seconds
    }
}
