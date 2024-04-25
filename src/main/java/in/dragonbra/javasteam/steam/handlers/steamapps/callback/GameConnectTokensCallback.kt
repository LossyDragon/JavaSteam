package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientGameConnectTokens
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import java.util.*

/**
 * This callback is fired when the client receives a list of game connect tokens.
 */
class GameConnectTokensCallback(msg: CMsgClientGameConnectTokens.Builder) : CallbackMsg() {

    /**
     * Gets a count of tokens to keep.
     * @return a count of tokens.
     */
    val tokensToKeep: Int = msg.maxTokensToKeep

    /**
     * Gets the list of tokens.
     * @return the list of tokens.
     */
    val tokens: List<ByteArray> = msg.tokensList.map { it.toByteArray() }
}
