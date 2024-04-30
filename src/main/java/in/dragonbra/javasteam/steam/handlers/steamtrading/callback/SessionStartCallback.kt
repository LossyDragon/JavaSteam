package `in`.dragonbra.javasteam.steam.handlers.steamtrading.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgTrading_StartSession
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.SteamID

/**
 * This callback is fired when a trading session has started.
 */
@Suppress("unused")
class SessionStartCallback(msg: CMsgTrading_StartSession.Builder) : CallbackMsg() {

    /**
     * Gets the SteamID of the client that this the trading session has started with.
     * @return the SteamID of the client that this the trading session has started with.
     */
    val otherClient: SteamID = SteamID(msg.otherSteamid)
}
