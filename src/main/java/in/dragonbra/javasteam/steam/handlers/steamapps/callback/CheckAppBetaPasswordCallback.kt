package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientCheckAppBetaPasswordResponse
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.util.Strings

/**
 * This callback is received when a beta password check has been completed
 */
class CheckAppBetaPasswordCallback(jobID: JobID, msg: CMsgClientCheckAppBetaPasswordResponse.Builder) : CallbackMsg() {

    /**
     * Result of the operation.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Map of beta names to their encryption keys
     * @return a map of beta names to their encryption keys.
     */
    val betaPasswords: Map<String, ByteArray> = msg.betapasswordsList.associateTo(mutableMapOf()) {
        it.betaname to Strings.decodeHex(it.betapassword)
    }

    init {
        this.jobID = jobID
    }
}
