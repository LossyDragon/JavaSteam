package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientGetAppOwnershipTicketResponse
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is received in response to calling [SteamApps.getAppOwnershipTicket]
 */
class AppOwnershipTicketCallback(jobID: JobID, msg: CMsgClientGetAppOwnershipTicketResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of requesting the ticket.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Gets the AppID this ticket is for.
     * @return the AppID.
     */
    val appID: Int = msg.appId

    /**
     * Gets the ticket data.
     * @return the ticket data.
     */
    val ticket: ByteArray = msg.ticket.toByteArray()

    init {
        this.jobID = jobID
    }
}
