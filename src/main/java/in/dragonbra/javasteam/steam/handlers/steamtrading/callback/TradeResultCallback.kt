package `in`.dragonbra.javasteam.steam.handlers.steamtrading.callback

import `in`.dragonbra.javasteam.enums.EEconTradeResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgTrading_InitiateTradeResponse
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.SteamID

/**
 * This callback is fired when this client receives the response from a trade proposal.
 */
@Suppress("unused")
class TradeResultCallback(msg: CMsgTrading_InitiateTradeResponse.Builder) : CallbackMsg() {

    /**
     * Gets the Trade ID that this result is for.
     * @return the Trade ID that this result is for.
     */
    val tradeID: Int = msg.tradeRequestId

    /**
     * Gets the response of the trade proposal.
     * @return the response of the trade proposal.
     */
    val response: EEconTradeResponse = EEconTradeResponse.from(msg.response)

    /**
     * Gets the SteamID of the client that responded to the proposal.
     * @return the [SteamID] of the client that responded to the proposal.
     */
    val otherClient: SteamID = SteamID(msg.otherSteamid)

    /**
     * Gets the number of days Steam Guard is required to have been active on this account.
     * @return the number of days Steam Guard is required to have been active on this account.
     */
    val numDaysSteamGuardRequired: Int = msg.steamguardRequiredDays

    /**
     * Gets the number of days a new device cannot trade for.
     * @return the number of days a new device cannot trade for.
     */
    val numDaysNewDeviceCooldown: Int = msg.newDeviceCooldownDays

    /**
     * Gets the default number of days one cannot trade for after a password reset.
     * @return the default number of days one cannot trade for after a password reset.
     */
    val defaultNumDaysPasswordResetProbation: Int = msg.defaultPasswordResetProbationDays

    /**
     * Gets the number of days one cannot trade for after a password reset.
     * @return the number of days one cannot trade for after a password reset.
     */
    val numDaysPasswordResetProbation: Int = msg.passwordResetProbationDays
}
