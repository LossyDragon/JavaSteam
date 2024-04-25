package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientGetCDNAuthTokenResponse
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID
import java.util.*

/**
 * This callback is received when a CDN auth token is received
 */
class CDNAuthTokenCallback(jobID: JobID, msg: CMsgClientGetCDNAuthTokenResponse.Builder) : CallbackMsg() {

    /**
     * Result of the operation.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * CDN auth token
     * @return the CDN auth token.
     */
    val token: String = msg.token

    /**
     * Token expiration date
     * @return the token expiration date.
     */
    val expiration: Date = Date(msg.expirationTime * 1000L)

    init {
        this.jobID = jobID
    }
}
