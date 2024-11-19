package `in`.dragonbra.javasteam.steam.handlers.steamauthticket.callback

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EAuthSessionResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientTicketAuthComplete
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.GameID
import `in`.dragonbra.javasteam.types.SteamID

/**
 * This callback is fired when generated ticket was successfully used to authenticate user.
 *
 * TODO: -- Experimental --
 */
class TicketAuthCompleteCallback(packetMsg: IPacketMsg) : CallbackMsg() {

    /**
     * Steam response to authentication request.
     */
    val authSessionResponse: EAuthSessionResponse?

    /**
     * Authentication state.
     */
    val state: Int

    /**
     * ID of the game the token was generated for.
     */
    val gameID: GameID

    /**
     * The [SteamID] of the game owner.
     */
    val ownerSteamID: SteamID

    /**
     * The [SteamID] of the game server.
     */
    val steamID: SteamID

    /**
     * CRC of the ticket.
     */
    val ticketCRC: Int

    /**
     * Sequence of the ticket.
     */
    val ticketSequence: Int

    init {
        val msg = ClientMsgProtobuf<CMsgClientTicketAuthComplete.Builder>(
            CMsgClientTicketAuthComplete::class.java,
            packetMsg
        )
        val resp = msg.body

        jobID = msg.targetJobID

        authSessionResponse = EAuthSessionResponse.from(resp.eauthSessionResponse)
        state = resp.estate
        gameID = GameID(resp.gameId)
        ownerSteamID = SteamID(resp.ownerSteamId)
        steamID = SteamID(resp.steamId)
        ticketCRC = resp.ticketCrc
        ticketSequence = resp.ticketSequence
    }
}
