package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientRedeemGuestPassResponse
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is received in response to activating a guest pass or a gift.
 */
@Suppress("unused")
class RedeemGuestPassResponseCallback(jobID: JobID, msg: CMsgClientRedeemGuestPassResponse.Builder) : CallbackMsg() {

    /**
     * Result of the operation
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Package ID which was activated.
     * @return the package ID.
     */
    val packageID: Int = msg.packageId

    /**
     * App ID which must be owned to activate this guest pass.
     * @return App ID which must be owned to activate this guest pass.
     */
    val mustOwnAppID: Int = msg.mustOwnAppid

    init {
        this.jobID = jobID
    }
}
