package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverAppinfo.CMsgClientPICSChangesSinceResponse
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSChangeData
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is fired when the PICS returns the changes since the last change number
 */
@Suppress("unused")
class PICSChangesCallback(jobID: JobID, msg: CMsgClientPICSChangesSinceResponse.Builder) : CallbackMsg() {

    /**
     * Supplied change number for the request
     * @return the supplied change number.
     */
    val lastChangeNumber: Int = msg.sinceChangeNumber

    /**
     * Gets the current change number
     * @return the current change number.
     */
    val currentChangeNumber: Int = msg.currentChangeNumber

    /**
     * If this update requires a full update of the information
     * @return if this update requires a full update.
     */
    val isRequiresFullUpdate: Boolean = msg.forceFullUpdate

    /**
     * If this update requires a full update of the app information
     * @return if this update requires a full update.
     */
    val isRequiresFullAppUpdate: Boolean = msg.forceFullAppUpdate

    /**
     * If this update requires a full update of the package information
     * @return if this update requires a full update.
     */
    val isRequiresFullPackageUpdate: Boolean = msg.forceFullPackageUpdate

    /**
     * Map containing requested package tokens
     * @return a map containing requested package tokens.
     */
    val packageChanges: Map<Int, PICSChangeData> = msg.packageChangesList.associateTo(mutableMapOf()) {
        it.packageid to PICSChangeData(it)
    }

    /**
     * Map containing requested package tokens
     * @return a map containing requested package tokens.
     */
    val appChanges: Map<Int, PICSChangeData> = msg.appChangesList.associateTo(mutableMapOf()) {
        it.appid to PICSChangeData(it)
    }

    init {
        this.jobID = jobID
    }
}
