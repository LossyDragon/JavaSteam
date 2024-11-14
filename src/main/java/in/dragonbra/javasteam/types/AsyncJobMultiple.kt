package `in`.dragonbra.javasteam.types

import `in`.dragonbra.javasteam.steam.steamclient.AsyncJobFailedException
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

/**
 * @author Lossy
 * @since 2023-03-17
 */
@Suppress("MemberVisibilityCanBePrivate")
class AsyncJobMultiple<T : CallbackMsg>(
    client: SteamClient,
    jobId: JobID,
    private val finishCondition: (T) -> Boolean,
) : AsyncJob(client, jobId) {

    data class ResultSet<T : CallbackMsg>(
        val complete: Boolean,
        val failed: Boolean = false,
        val results: List<T>,
    )

    private val deferred = CompletableDeferred<ResultSet<T>>()

    private val results = mutableListOf<T>()

    /**
     * Awaits the result set of this async job.
     */
    suspend fun await(): ResultSet<T> = deferred.await()

    fun asDeferred(): Deferred<ResultSet<T>> = deferred

    /* Java Compat */
    fun runBlocking(): CompletableFuture<ResultSet<T>> = scope.future {
        await()
    }

    override fun addResult(callback: CallbackMsg): Boolean {
        @Suppress("UNCHECKED_CAST")
        val callbackMsg = callback as T
        results.add(callbackMsg)

        return if (finishCondition(callbackMsg)) {
            deferred.complete(ResultSet(complete = true, results = results.toList()))
            true
        } else {
            heartbeat()
            false
        }
    }

    override fun setFailed(dueToRemoteFailure: Boolean) {
        if (results.isEmpty()) {
            if (dueToRemoteFailure) {
                deferred.completeExceptionally(AsyncJobFailedException())
            } else {
                deferred.cancel()
            }
        } else {
            deferred.complete(
                ResultSet(
                    complete = false,
                    failed = dueToRemoteFailure,
                    results = results.toList()
                )
            )
        }
    }
}
