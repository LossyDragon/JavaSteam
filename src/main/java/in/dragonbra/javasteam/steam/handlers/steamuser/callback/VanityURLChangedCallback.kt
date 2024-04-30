package `in`.dragonbra.javasteam.steam.handlers.steamuser.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientVanityURLChangedNotification
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is received when users' vanity url changes.
 */
class VanityURLChangedCallback(jobID: JobID, msg: CMsgClientVanityURLChangedNotification.Builder) : CallbackMsg() {

    /**
     * Gets the new vanity url.
     * @return the new vanity url.
     */
    val vanityUrl: String = msg.vanityUrl

    init {
        this.jobID = jobID
    }
}
