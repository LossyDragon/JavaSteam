package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.enums.EPurchaseResultDetail
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientPurchaseResponse
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.stream.MemoryStream
import java.io.IOException

/**
 * This callback is received in a response to activating a Steam key.
 */
@Suppress("unused")
class PurchaseResponseCallback(jobID: JobID, msg: CMsgClientPurchaseResponse.Builder) : CallbackMsg() {

    companion object {
        private val logger = LogManager.getLogger(PurchaseResponseCallback::class.java)
    }

    /**
     * Result of the operation.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Purchase result of the operation
     * @return the purchase result of the operation
     */
    val purchaseResultDetail: EPurchaseResultDetail = EPurchaseResultDetail.from(msg.purchaseResultDetails)

    /**
     * Purchase receipt of the operation
     * @return the purchase receipt of the operation
     */
    val purchaseReceiptInfo: KeyValue = KeyValue()

    init {
        this.jobID = jobID

        msg.purchaseReceiptInfo?.let {
            try {
                val ms = MemoryStream(msg.purchaseReceiptInfo.toByteArray())
                purchaseReceiptInfo.tryReadAsBinary(ms)
            } catch (exception: IOException) {
                logger.error("input stream is null", exception)
            }
        }
    }
}
