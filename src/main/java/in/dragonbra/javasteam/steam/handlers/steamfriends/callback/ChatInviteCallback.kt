package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.enums.EChatRoomType
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientChatInvite
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.GameID
import `in`.dragonbra.javasteam.types.SteamID

/**
 * This callback is fired when a chat invite is received.
 */
@Suppress("unused")
class ChatInviteCallback(invite: CMsgClientChatInvite.Builder) : CallbackMsg() {

    /**
     * Gets the SteamID of the user who was invited to the chat.
     * @return the [SteamID] of the user who was invited to the chat.
     */
    val invitedID: SteamID = SteamID(invite.steamIdInvited)

    /**
     * Gets the chat room SteamID.
     * @return the chat room [SteamID].
     */
    val chatRoomID: SteamID = SteamID(invite.steamIdChat)

    /**
     * Gets the SteamID of the user who performed the invitation.
     * @return the [SteamID] of the user who performed the invitation.
     */
    val patronID: SteamID = SteamID(invite.steamIdPatron)

    /**
     * Gets the chat room type.
     * @return the chat room type.
     */
    val chatRoomType: EChatRoomType? = EChatRoomType.from(invite.chatroomType)

    /**
     * Gets the SteamID of the chat friend.
     * @return the [SteamID] of the chat friend.
     */
    val friendChatID: SteamID = SteamID(invite.steamIdFriendChat)

    /**
     * Gets the name of the chat room.
     * @return the name of the chat room.
     */
    val chatRoomName: String = invite.chatName

    /**
     * Gets the GameID associated with this chat room, if it's a game lobby.
     * @return the [GameID] associated with this chat room, if it's a game lobby.
     */
    val gameID: GameID = GameID(invite.gameId)
}
