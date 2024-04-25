package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverAppinfo.CMsgClientPICSAccessTokenResponse
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID
import java.util.*

/**
 * This callback is fired when the PICS returns access tokens for a list of appids and packageids
 */
class PICSTokensCallback(jobID: JobID, msg: CMsgClientPICSAccessTokenResponse.Builder) : CallbackMsg() {

    /**
     * Gets a list of denied package tokens
     * @return a list of denied package tokens.
     */
    val packageTokensDenied: List<Int> = msg.packageDeniedTokensList

    /**
     * Gets a list of denied app tokens
     * @return a list of denied app tokens.
     */
    val appTokensDenied: List<Int> = msg.appDeniedTokensList

    /**
     * Map containing requested package tokens
     * @return a map containing requested package tokens.
     */
    val packageTokens: Map<Int, Long> = msg.packageAccessTokensList.associateTo(mutableMapOf()) {
        it.packageid to it.accessToken
    }

    /**
     * Map containing requested app tokens
     * @return a map containing requested app tokens.
     */
    val appTokens: Map<Int, Long> = msg.appAccessTokensList.associateTo(mutableMapOf()) {
        it.appid to it.accessToken
    }

    init {
        this.jobID = jobID
    }
}
