package `in`.dragonbra.javasteam.steam.handlers.steamgameserver.callback

import `in`.dragonbra.javasteam.enums.EAuthSessionResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientTicketAuthComplete
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.GameID
import `in`.dragonbra.javasteam.types.SteamID

/**
 * This callback is fired when ticket authentication has completed.
 */
@Suppress("unused")
class TicketAuthCallback(tickAuth: CMsgClientTicketAuthComplete.Builder) : CallbackMsg() {

    /**
     * Gets the SteamID the ticket auth completed for.
     * @return the SteamID the ticket auth completed for
     */
    val steamID: SteamID = SteamID(tickAuth.steamId)

    /**
     * Gets the GameID the ticket was for.
     * @return the GameID the ticket was for
     */
    val gameID: GameID = GameID(tickAuth.gameId)

    /**
     * Gets the authentication state.
     * @return the authentication state
     */
    val state: Int = tickAuth.estate

    /**
     * Gets the auth session response.
     * @return the auth session response
     */
    val authSessionResponse: EAuthSessionResponse = EAuthSessionResponse.from(tickAuth.eauthSessionResponse)

    /**
     * Gets the ticket CRC.
     * @return the ticket CRC
     */
    val ticketCrc: Int = tickAuth.ticketCrc

    /**
     * Gets the ticket sequence.
     * @return the ticket sequence
     */
    val ticketSequence: Int = tickAuth.ticketSequence
}
