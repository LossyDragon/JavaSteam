package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.generated.MsgClientUpdateGuestPassesList
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.util.log.LogManager
import java.io.IOException
import java.io.InputStream

/**
 * This callback is received when the list of guest passes is updated.
 */
class GuestPassListCallback(msg: MsgClientUpdateGuestPassesList, payload: InputStream) : CallbackMsg() {

    companion object {
        private val logger = LogManager.getLogger(GuestPassListCallback::class.java)
    }

    /**
     * Result of the operation.
     * @return the result.
     */
    val result: EResult = msg.result

    /**
     * Number of guest passes to be given out.
     * @return the number of guest passes to be given out.
     */
    val countGuestPassesToGive: Int = msg.countGuestPassesToGive

    /**
     * Number of guest passes to be redeemed
     * @return the number of guest passes to be redeemed.
     */
    val countGuestPassesToRedeem: Int = msg.countGuestPassesToRedeem

    /**
     * Guest pass list
     * @return the guest pass list.
     */
    var guestPasses: List<KeyValue> = listOf()
        private set

    init {
        val tempList = mutableListOf<KeyValue>()
        try {
            for (i in 0 until countGuestPassesToGive + countGuestPassesToRedeem) {
                val kv = KeyValue()
                kv.tryReadAsBinary(payload)
                tempList.add(kv)
            }
            guestPasses = tempList
        } catch (e: IOException) {
            logger.error("failed to read guest passes", e)
        }
    }
}
