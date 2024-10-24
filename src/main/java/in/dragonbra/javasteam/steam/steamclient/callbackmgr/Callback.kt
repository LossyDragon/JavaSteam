package `in`.dragonbra.javasteam.steam.steamclient.callbackmgr

import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.io.Closeable

@Suppress("unused")
class Callback<TCall : CallbackMsg> @JvmOverloads constructor(
    private val callbackClass: Class<out TCall>,
    func: Consumer<TCall>? = null,
    mgr: ICallbackMgrInternals? = null,
    var jobID: JobID = JobID.INVALID,
) : CallbackBase(),
    Closeable {

//    companion object {
//        @JvmStatic
//        fun <TCall : CallbackMsg> create(
//            callbackClass: Class<TCall>,
//            func: Consumer<TCall>,
//            mgr: ICallbackMgrInternals? = null,
//            jobId: JobID = JobID.INVALID
//        ): Callback<TCall> = Callback(callbackClass, func, mgr, jobId)
//    }

    private var mgr: ICallbackMgrInternals? = null

    private val onRun = func

    override val callbackType: Class<out CallbackMsg>
        get() = callbackClass

    init {
        attachTo(mgr)
    }

    private fun attachTo(mgr: ICallbackMgrInternals?) {
        this.mgr = mgr ?: return
        mgr.register(this)
    }

    override fun close() {
        mgr?.unregister(this)
        mgr = null
    }

    @Suppress("UNCHECKED_CAST")
    override fun run(callback: Any) {
        if (callbackClass.isInstance(callback)) {
            val cb = callback as? TCall
            if (cb != null && (cb.jobID == jobID || jobID == JobID.INVALID)) {
                onRun?.accept(cb)
            }
        }
    }
}
