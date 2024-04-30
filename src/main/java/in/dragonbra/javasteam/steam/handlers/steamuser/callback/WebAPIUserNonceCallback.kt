package `in`.dragonbra.javasteam.steam.handlers.steamuser.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientRequestWebAPIAuthenticateUserNonceResponse
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is received when requesting a new WebAPI authentication user nonce.
 */
class WebAPIUserNonceCallback(jobID: JobID, body: CMsgClientRequestWebAPIAuthenticateUserNonceResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of the request.
     * @return the result.
     */
    val result: EResult = EResult.from(body.eresult)

    /**
     * Gets the authentication nonce.
     * @return the authentication nonce.
     */
    val nonce: String = body.webapiAuthenticateUserNonce

    init {
        this.jobID = jobID
    }
}
