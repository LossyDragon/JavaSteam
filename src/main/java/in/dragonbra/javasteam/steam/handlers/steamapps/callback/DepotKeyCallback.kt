package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientGetDepotDecryptionKeyResponse
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is received in response to calling [SteamApps.getDepotDecryptionKey]
 */
class DepotKeyCallback(jobID: JobID, msg: CMsgClientGetDepotDecryptionKeyResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of requesting this encryption key.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Gets the DepotID this encryption key is for.
     * @return the DepotID.
     */
    val depotID: Int = msg.depotId

    /**
     * Gets the encryption key for this depot.
     * @return the encryption key.
     */
    val depotKey: ByteArray = msg.depotEncryptionKey.toByteArray()

    init {
        this.jobID = jobID
    }
}
