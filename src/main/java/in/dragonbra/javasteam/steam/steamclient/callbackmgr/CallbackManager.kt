package `in`.dragonbra.javasteam.steam.steamclient.callbackmgr

import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.io.Closeable
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * This class is a utility for routing callbacks to function calls.
 * In order to bind callbacks to functions, an instance of this class must be created for the
 * [SteamClient] instance that will be posting callbacks.
 *
 * @constructor Initializes a new instance of the [CallbackManager] class.
 * @param steamClient The [SteamClient] instance to handle the callbacks of.
 */
class CallbackManager(private val steamClient: SteamClient) : ICallbackMgrInternals {

    private val registeredCallbacks: MutableSet<CallbackBase> = Collections.newSetFromMap(ConcurrentHashMap())

    /**
     * Runs a single queued callback.
     * If no callback is queued, this method will instantly return.
     */
    fun runCallbacks() {
        steamClient.getCallback(true).also(::handle)
    }

    /**
     * Blocks the current thread to run a single queued callback.
     * If no callback is queued, the method will block for the given timeout.
     * @param timeout The length of time to block.
     */
    fun runWaitCallbacks(timeout: Long) {
        steamClient.waitForCallback(true, timeout).also(::handle)
    }

    /**
     * Blocks the current thread to run a single queued callback.
     * If no callback is queued, the method will block until one is posted.
     */
    fun runWaitCallbacks() {
        steamClient.waitForCallback(true).also(::handle)
    }

    /**
     * Blocks the current thread to run all queued callbacks.
     * If no callback is queued, the method will block for the given timeout.
     * @param timeout The length of time to block.
     */
    fun runWaitAllCallbacks(timeout: Long) {
        steamClient.getAllCallbacks(true, timeout).forEach(::handle)
    }

    /**
     * Registers the provided [Consumer] to receive callbacks of type [TCallback]
     * @param TCallback  The type of callback to subscribe to.
     * @param callbackType type of the callback
     * @param jobID        The [JobID]  of the callbacks that should be subscribed to. If this is [JobID.INVALID], [TCallback] will be received.
     * @param callbackFunc The function to invoke with the callback.
     * @return An [Closeable]. Disposing of the return value will unsubscribe the callbackFunc .
     */
    fun <TCallback : ICallbackMsg> subscribe(
        callbackType: Class<out TCallback>,
        jobID: JobID,
        callbackFunc: Consumer<TCallback>,
    ): Closeable {
        val callback = Callback(callbackType, callbackFunc, this, jobID)
        return Subscription(this, callback)
    }

    /**
     * Registers the provided [Consumer] to receive callbacks of type [TCallback]
     * @param TCallback   The type of callback to subscribe to.
     * @param callbackType type of the callback
     * @param callbackFunc The function to invoke with the callback.
     * @return An [Closeable]. Disposing of the return value will unsubscribe the callbackFunc .
     */
    fun <TCallback : ICallbackMsg> subscribe(
        callbackType: Class<out TCallback>,
        callbackFunc: Consumer<TCallback>,
    ): Closeable {
        return subscribe(callbackType, JobID.INVALID, callbackFunc)
    }

    override fun register(callback: CallbackBase) {
        registeredCallbacks.add(callback)
    }

    override fun unregister(callback: CallbackBase) {
        registeredCallbacks.remove(callback)
    }

    private fun handle(call: ICallbackMsg) {
        registeredCallbacks.forEach { callback ->
            if (callback.callbackType.isAssignableFrom(call.javaClass)) {
                callback.run(call)
            }
        }
    }
}
