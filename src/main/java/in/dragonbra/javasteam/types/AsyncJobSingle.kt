package `in`.dragonbra.javasteam.types

import `in`.dragonbra.javasteam.steam.steamclient.AsyncJobFailedException
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

/**
 * Represents a coroutine-based async job that returns a single result.
 * @author Lossy
 * @since 2023-03-17
 */
class AsyncJobSingle<T : CallbackMsg>(client: SteamClient, jobId: JobID) : AsyncJob(client, jobId) {

    private val deferred = CompletableDeferred<T>()

    /**
     * Awaits the result of this async job.
     */
    suspend fun await(): T = deferred.await()

    fun asDeferred(): Deferred<T> = deferred

    /* Java Compat */
    fun runBlocking(): CompletableFuture<T> = CoroutineScope(Dispatchers.IO).future {
        await()
    }

    override fun addResult(callback: CallbackMsg): Boolean {
        @Suppress("UNCHECKED_CAST")
        deferred.complete(callback as T)
        return true // Single result job is always complete after first result
    }

    override fun setFailed(dueToRemoteFailure: Boolean) {
        if (dueToRemoteFailure) {
            deferred.completeExceptionally(AsyncJobFailedException())
        } else {
            deferred.cancel()
        }
    }
}
