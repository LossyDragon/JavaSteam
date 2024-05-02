package `in`.dragonbra.javasteam.steam.handlers.steammasterserver.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverGameservers.CMsgGMSClientServerQueryResponse
import `in`.dragonbra.javasteam.steam.handlers.steammasterserver.Server
import `in`.dragonbra.javasteam.steam.handlers.steammasterserver.SteamMasterServer
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is received in response to calling [SteamMasterServer.serverQuery].
 */
class QueryCallback(jobID: JobID, msg: CMsgGMSClientServerQueryResponse.Builder) : CallbackMsg() {

    /**
     * Gets the list of servers.
     * @return the list of servers
     */
    val servers: List<Server> = msg.serversList.map { Server(it) }

    init {
        this.jobID = jobID
    }
}
