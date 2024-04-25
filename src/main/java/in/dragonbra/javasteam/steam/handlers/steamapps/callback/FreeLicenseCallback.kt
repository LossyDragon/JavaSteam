package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientRequestFreeLicenseResponse
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is received in response to calling [SteamApps.requestFreeLicense],
 * informing the client of newly granted packages, if any.
 */
@Suppress("unused")
class FreeLicenseCallback(jobID: JobID, msg: CMsgClientRequestFreeLicenseResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of the message.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Gets the list of granted apps.
     * @return the list of granted apps.
     */
    val grantedApps: List<Int> = msg.grantedAppidsList

    /**
     * Gets the list of granted packages.
     * @return the list of granted packages.
     */
    val grantedPackages: List<Int> = msg.grantedPackageidsList

    init {
        this.jobID = jobID
    }
}
