package `in`.dragonbra.javasteam.steam.handlers.steamgameserver.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverGameservers.CMsgGSStatusReply
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg

/**
 * This callback is fired when the game server receives a status reply.
 */
class StatusReplyCallback(reply: CMsgGSStatusReply.Builder) : CallbackMsg() {

    /**
     * Gets a value indicating whether this game server is VAC secure.
     * @return **true** if this server is VAC secure; otherwise, **false**.
     */
    val isSecure: Boolean = reply.isSecure
}
