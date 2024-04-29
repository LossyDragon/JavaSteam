package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientAMGetPersonaNameHistoryResponse
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.NameTableInstance
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * Callback fired in response to calling [SteamFriends.requestAliasHistory].
 */
class AliasHistoryCallback(jobID: JobID, msg: CMsgClientAMGetPersonaNameHistoryResponse.Builder) : CallbackMsg() {

    /**
     * Gets a list of previous names.
     * @return the responses to the steam ids
     */
    var responses: List<NameTableInstance> = msg.responsesList.map { NameTableInstance(it) }

    init {
        this.jobID = jobID
    }
}
