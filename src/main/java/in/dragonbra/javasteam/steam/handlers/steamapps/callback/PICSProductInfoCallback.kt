package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverAppinfo.CMsgClientPICSProductInfoResponse
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSProductInfo
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is fired when the PICS returns the product information requested
 */
@Suppress("unused")
class PICSProductInfoCallback(jobID: JobID, msg: CMsgClientPICSProductInfoResponse.Builder) : CallbackMsg() {

    /**
     * Gets if this response contains only product metadata
     * @return if this response contains only product metadata.
     */
    val isMetaDataOnly: Boolean = msg.metaDataOnly

    /**
     * Gets if there are more product information responses pending
     * @return if there are more product information responses pending.
     */
    val isResponsePending: Boolean = msg.responsePending

    /**
     * Gets a list of unknown package ids
     * @return a list of unknown package ids.
     */
    val unknownPackages: List<Int> = msg.unknownPackageidsList

    /**
     * Gets a list of unknown app ids
     * @return a list of unknown app ids.
     */
    val unknownApps: List<Int> = msg.unknownAppidsList

    /**
     * Map containing requested app info
     * @return a map containing requested app info.
     */
    val apps: Map<Int, PICSProductInfo> = msg.appsList.associateTo(mutableMapOf()) {
        it.appid to PICSProductInfo(msg, it)
    }

    /**
     * Map containing requested package info
     * @return a map containing requested package info.
     */
    val packages: Map<Int, PICSProductInfo> = msg.packagesList.associateTo(mutableMapOf()) {
        it.packageid to PICSProductInfo(it)
    }

    init {
        this.jobID = jobID
    }
}
