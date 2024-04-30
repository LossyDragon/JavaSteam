package `in`.dragonbra.javasteam.steam.handlers.steamtrading.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgTrading_InitiateTradeRequest
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.SteamID

/**
 * This callback is fired when this client receives a trade proposal.
 */
@Suppress("unused")
class TradeProposedCallback(msg: CMsgTrading_InitiateTradeRequest.Builder) : CallbackMsg() {

    /**
     * Gets the Trade ID of his proposal, used for replying.
     * @return the Trade ID of his proposal, used for replying.
     */
    val tradeID: Int = msg.tradeRequestId

    /**
     * Gets the SteamID of the client that sent the proposal.
     * @return the SteamID of the client that sent the proposal.
     */
    val otherClient: SteamID = SteamID(msg.otherSteamid)
}
