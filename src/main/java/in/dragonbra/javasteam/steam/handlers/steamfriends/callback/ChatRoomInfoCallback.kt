package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.enums.EChatInfoType
import `in`.dragonbra.javasteam.generated.MsgClientChatRoomInfo
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.SteamID

/**
 * This callback is fired in response to chat room info being received.
 */
@Suppress("UNUSED_PARAMETER", "RedundantEmptyInitializerBlock")
class ChatRoomInfoCallback(msg: MsgClientChatRoomInfo, payload: ByteArray) : CallbackMsg() {

    /**
     * Gets SteamId of the chat room.
     * @return [SteamID] of the chat room.
     */
    val chatRoomID: SteamID = msg.steamIdChat

    /**
     * Gets the info type.
     * @return the info type.
     */
    val type: EChatInfoType = msg.type

    init {
        // todo: handle inner payload based on the type similar to ChatMemberInfoCallback
    }
}
