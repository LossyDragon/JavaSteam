package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.generated.MsgClientGetLegacyGameKeyResponse
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is received in response to calling [SteamApps.getLegacyGameKey].
 */
class LegacyGameKeyCallback(
    jobID: JobID,
    msg: MsgClientGetLegacyGameKeyResponse,
    payload: ByteArray,
) : CallbackMsg() {

    /**
     * Gets the result of requesting this game key.
     * @return the result.
     */
    val result: EResult = msg.result

    /**
     * Gets the appid that this game key is for.
     * @return the appid.
     */
    val appID: Int = msg.appId

    /**
     * Gets the game key.
     * @return the game key.
     */
    var key: String? = null

    init {
        this.jobID = jobID

        if (msg.length > 0) {
            val length: Int = msg.length - 1
            key = String(payload, 0, length)
        }
    }
}
