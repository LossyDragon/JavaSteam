package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.generated.MsgClientVACBanStatus
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.stream.BinaryReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

/**
 * This callback is fired when the client receives its VAC banned status.
 */
class VACStatusCallback(msg: MsgClientVACBanStatus, payload: ByteArray) : CallbackMsg() {

    companion object {
        private val logger = LogManager.getLogger(VACStatusCallback::class.java)
    }

    /**
     * Gets a list of VAC banned apps the client is banned from.
     * @return a list of VAC banned apps.
     */
    var bannedApps: List<Int> = listOf()
        private set

    init {
        val tempList: MutableList<Int> = ArrayList()
        try {
            BinaryReader(ByteArrayInputStream(payload)).use { br ->
                for (i in 0 until msg.numBans) {
                    tempList.add(br.readInt())
                }
            }
            bannedApps = tempList.toList()
        } catch (e: IOException) {
            logger.error("Failed to read bans. Returning empty list", e)
        }
    }
}
