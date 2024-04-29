package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.enums.EChatEntryType
import `in`.dragonbra.javasteam.generated.MsgClientChatMsg
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.SteamID
import java.nio.charset.StandardCharsets

/**
 * This callback is fired when a chat room message arrives.
 */
@Suppress("unused")
class ChatMsgCallback(msg: MsgClientChatMsg, payload: ByteArray) : CallbackMsg() {

    /**
     * Gets the SteamID of the chatter.
     * @return the [SteamID] of the chatter.
     */
    val chatterID: SteamID = msg.steamIdChatter

    /**
     * Gets the SteamID of the chat room.
     * @return the [SteamID] of the chat room.
     */
    val chatRoomID: SteamID = msg.steamIdChatRoom

    /**
     * Gets chat entry type.
     * @return chat entry type.
     */
    val chatMsgType: EChatEntryType = msg.chatMsgType

    /**
     *  Gets the message.
     * @return the message.
     */
    // trim any extra null chars from the end
    val message: String = String(payload, StandardCharsets.UTF_8).replace("\u0000+$".toRegex(), "")
}
