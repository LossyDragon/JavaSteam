package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.enums.EChatRoomEnterResponse
import `in`.dragonbra.javasteam.enums.EChatRoomType
import `in`.dragonbra.javasteam.generated.MsgClientChatEnter
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.ChatMemberInfo
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.stream.BinaryReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * This callback is fired in response to attempting to join a chat.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ChatEnterCallback(msg: MsgClientChatEnter, payload: ByteArray) : CallbackMsg() {

    /**.
     * Gets SteamID of the chat room.
     * @return the [SteamID] of the chat room.
     */
    val chatID: SteamID = msg.steamIdChat

    /**
     * Gets the friend ID.
     * @return the friend ID.
     */
    val friendID: SteamID = msg.steamIdFriend

    /**
     * Gets the type of the chat room.
     * @return the type of the chat room.
     */
    val chatRoomType: EChatRoomType = msg.chatRoomType

    /**
     * Gets the SteamID of the chat room owner.
     * @return the [SteamID] of the chat room owner.
     */
    val ownerID: SteamID = msg.steamIdOwner

    /**
     * Gets clan SteamID that owns this chat room.
     * @return the clan [SteamID] that owns this chat room.
     */
    val clanID: SteamID = msg.steamIdClan

    /**
     * Gets the chat flags.
     * @return the chat flags.
     */
    val chatFlags: Byte = msg.chatFlags

    /**
     * Gets the chat enter response.
     * @return the chat enter response.
     */
    val enterResponse: EChatRoomEnterResponse = msg.enterResponse

    /**
     * Gets the number of users currently in this chat room.
     * @return the number of users currently in this chat room.
     */
    val numChatMembers: Int = msg.numMembers

    /**
     * Gets the name of the chat room.
     * @return the name of the chat room.
     */
    var chatRoomName: String? = null
        private set

    /**
     * Gets a list of [ChatMemberInfo] instances for each of the members of this chat room.
     * @return a list of [ChatMemberInfo] instances for each of the members of this chat room.
     */
    var chatMembers: List<ChatMemberInfo>? = null
        private set

    init {
        val bais = ByteArrayInputStream(payload)

        try {
            BinaryReader(bais).use { br ->
                // steamclient always attempts to read the chat room name, regardless of the enter response
                chatRoomName = br.readNullTermString(StandardCharsets.UTF_8)

                // the rest of the payload depends on a successful chat enter
                if (enterResponse == EChatRoomEnterResponse.Success) {
                    chatMembers = (0 until numChatMembers).map {
                        ChatMemberInfo().apply {
                            readFromStream(br)
                        }
                    }
                }
            }
        } catch (ignored: IOException) {
        }
    }
}
