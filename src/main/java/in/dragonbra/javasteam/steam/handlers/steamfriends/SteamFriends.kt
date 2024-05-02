package `in`.dragonbra.javasteam.steam.handlers.steamfriends

import com.google.protobuf.ByteString
import `in`.dragonbra.javasteam.base.ClientMsg
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EChatAction
import `in`.dragonbra.javasteam.enums.EChatEntryType
import `in`.dragonbra.javasteam.enums.EChatInfoType
import `in`.dragonbra.javasteam.enums.EChatMemberStateChange
import `in`.dragonbra.javasteam.enums.EClanRelationship
import `in`.dragonbra.javasteam.enums.EClientPersonaStateFlag
import `in`.dragonbra.javasteam.enums.EFriendRelationship
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.enums.EPersonaState
import `in`.dragonbra.javasteam.enums.EPersonaStateFlag
import `in`.dragonbra.javasteam.generated.MsgClientChatAction
import `in`.dragonbra.javasteam.generated.MsgClientChatActionResult
import `in`.dragonbra.javasteam.generated.MsgClientChatEnter
import `in`.dragonbra.javasteam.generated.MsgClientChatMemberInfo
import `in`.dragonbra.javasteam.generated.MsgClientChatMsg
import `in`.dragonbra.javasteam.generated.MsgClientChatRoomInfo
import `in`.dragonbra.javasteam.generated.MsgClientJoinChat
import `in`.dragonbra.javasteam.generated.MsgClientSetIgnoreFriend
import `in`.dragonbra.javasteam.generated.MsgClientSetIgnoreFriendResponse
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientAMGetPersonaNameHistory
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientAMGetPersonaNameHistoryResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientChatInvite
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientClanState
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientChatGetFriendMessageHistory
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientChatGetFriendMessageHistoryForOfflineMessages
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientChatGetFriendMessageHistoryResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientAddFriend
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientAddFriendResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientChangeStatus
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientFriendMsg
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientFriendMsgIncoming
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientFriendProfileInfo
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientFriendProfileInfoResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientFriendsList
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientPersonaState
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientPlayerNicknameList
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientRemoveFriend
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientRequestFriendData
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientSetPlayerNickname
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientSetPlayerNicknameResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgPersonaChangeResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientAccountInfo
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.cache.AccountCache
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.cache.Clan
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.AliasHistoryCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.ChatActionResultCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.ChatEnterCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.ChatInviteCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.ChatMemberInfoCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.ChatMsgCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.ChatRoomInfoCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.ClanStateCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.FriendAddedCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.FriendMsgCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.FriendMsgEchoCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.FriendMsgHistoryCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.FriendsListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.IgnoreFriendCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.NicknameCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.NicknameListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.PersonaChangeCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.PersonaStatesCallback
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.ProfileInfoCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.AccountInfoCallback
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import `in`.dragonbra.javasteam.types.GameID
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.types.SteamID.ChatInstanceFlags
import `in`.dragonbra.javasteam.util.compat.Consumer
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.EnumMap

/**
 * This handler handles all interaction with other users on the Steam3 network.
 */
@Suppress("unused")
class SteamFriends : ClientMsgHandler() {

    private val friendList: MutableList<SteamID> = mutableListOf()

    private val clanList: MutableList<SteamID> = mutableListOf()

    private val cache: AccountCache = AccountCache()

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.ClientPersonaState] = Consumer(::handlePersonaState)
        dispatchMap[EMsg.ClientClanState] = Consumer(::handleClanState)
        dispatchMap[EMsg.ClientFriendsList] = Consumer(::handleFriendsList)
        dispatchMap[EMsg.ClientFriendMsgIncoming] = Consumer(::handleFriendMsg)
        dispatchMap[EMsg.ClientFriendMsgEchoToSender] = Consumer(::handleFriendEchoMsg)
        dispatchMap[EMsg.ClientChatGetFriendMessageHistoryResponse] = Consumer(::handleFriendMessageHistoryResponse)
        dispatchMap[EMsg.ClientAccountInfo] = Consumer(::handleAccountInfo)
        dispatchMap[EMsg.ClientAddFriendResponse] = Consumer(::handleFriendResponse)
        dispatchMap[EMsg.ClientChatEnter] = Consumer(::handleChatEnter)
        dispatchMap[EMsg.ClientChatMsg] = Consumer(::handleChatMsg)
        dispatchMap[EMsg.ClientChatMemberInfo] = Consumer(::handleChatMemberInfo)
        dispatchMap[EMsg.ClientChatRoomInfo] = Consumer(::handleChatRoomInfo)
        dispatchMap[EMsg.ClientChatActionResult] = Consumer(::handleChatActionResult)
        dispatchMap[EMsg.ClientChatInvite] = Consumer(::handleChatInvite)
        dispatchMap[EMsg.ClientSetIgnoreFriendResponse] = Consumer(::handleIgnoreFriendResponse)
        dispatchMap[EMsg.ClientFriendProfileInfoResponse] = Consumer(::handleProfileInfoResponse)
        dispatchMap[EMsg.ClientPersonaChangeResponse] = Consumer(::handlePersonaChangeResponse)
        dispatchMap[EMsg.AMClientSetPlayerNicknameResponse] = Consumer(::handlePlayerNicknameResponse)
        dispatchMap[EMsg.ClientAMGetPersonaNameHistoryResponse] = Consumer(::handleAliasHistoryResponse)
        dispatchMap[EMsg.ClientPlayerNicknameList] = Consumer(::handleNicknameList)
    }

    /**
     * Gets the local user's persona name. Will be null before user initialization.
     *  User initialization is performed prior to [AccountInfoCallback] callback.
     * @return the local username.
     */
    fun getPersonaName(): String? {
        return cache.localUser.name
    }

    /**
     * Sets the local user's persona name and broadcasts it over the network.
     *  Results are returned in a [PersonaChangeCallback] callback.
     * @param name The name.
     */
    fun setPersonaName(name: String) {
        // cache the local name right away, so that early calls to SetPersonaState don't reset the set name
        cache.localUser.name = name

        ClientMsgProtobuf<CMsgClientChangeStatus.Builder>(
            CMsgClientChangeStatus::class.java,
            EMsg.ClientChangeStatus
        ).apply {
            cache.localUser.personaState?.code()?.let { body.setPersonaState(it) }
            body.setPlayerName(name)
        }.also(client::send)
    }

    /**
     * Gets the local user's persona state.
     * @return The persona state.
     */
    fun getPersonaState(): EPersonaState? {
        return cache.localUser.personaState
    }

    /**
     * Sets the local user's persona state and broadcasts it over the network.
     *  Results are returned in a [PersonaChangeCallback] callback.
     * @param state The state.
     */
    fun setPersonaState(state: EPersonaState) {
        cache.localUser.personaState = state

        ClientMsgProtobuf<CMsgClientChangeStatus.Builder>(
            CMsgClientChangeStatus::class.java,
            EMsg.ClientChangeStatus
        ).apply {
            body.setPersonaState(state.code())
            body.setPersonaSetByUser(true)
        }.also(client::send)
    }

    /**
     * JavaSteam addition:
     *  Sets the local user's persona state flag back to normal desktop mode.
     */
    fun resetPersonaStateFlag() {
        ClientMsgProtobuf<CMsgClientChangeStatus.Builder>(
            CMsgClientChangeStatus::class.java,
            EMsg.ClientChangeStatus
        ).apply {
            body.setPersonaSetByUser(true)
            body.setPersonaStateFlags(0)
        }.also(client::send)
    }

    /**
     * JavaSteam addition:
     *  Sets the local user's persona state flag to a valid ClientType
     * @param flag one of the following
     * [EPersonaStateFlag.ClientTypeWeb],
     * [EPersonaStateFlag.ClientTypeMobile],
     * [EPersonaStateFlag.ClientTypeTenfoot],
     * [EPersonaStateFlag.ClientTypeVR],
     * or [EPersonaStateFlag.LaunchTypeGamepad].
     */
    fun setPersonaStateFlag(flag: EPersonaStateFlag) {
        require(flag >= EPersonaStateFlag.ClientTypeWeb) {
            "Persona State Flag was not a valid ClientType"
        }
        require(flag <= EPersonaStateFlag.LaunchTypeGamepad) {
            "Persona State Flag was not a valid ClientType"
        }

        ClientMsgProtobuf<CMsgClientChangeStatus.Builder>(
            CMsgClientChangeStatus::class.java,
            EMsg.ClientChangeStatus
        ).apply {
            body.setPersonaSetByUser(true)
            body.setPersonaStateFlags(flag.code())
        }.also(client::send)
    }

    /**
     * Gets the friend count of the local user.
     * @return the number of friends.
     */
    fun getFriendCount(): Int {
        // lock it?
        return friendList.size
    }

    /**
     * Gets a friend by index.
     * @param index the index.
     * @return A valid [SteamID] of a friend if the index is in range; otherwise a [SteamID] representing 0.
     */
    fun getFriendByIndex(index: Int): SteamID {
        // lock it?
        if (index < 0 || index >= friendList.size) {
            return SteamID(0)
        }

        return friendList[index]
    }

    /**
     * Gets the persona name of a friend.
     * @param steamID the [SteamID].
     * @return the name.
     */
    fun getFriendPersonaName(steamID: SteamID): String? {
        return cache.getUser(steamID).name
    }

    /**
     * Gets the persona state of a friend.
     * @param steamID the [SteamID].
     * @return the persona state, or null if not set.
     */
    fun getFriendPersonaState(steamID: SteamID): EPersonaState? {
        return cache.getUser(steamID).personaState
    }

    /**
     * Gets the relationship of a friend.
     * @param steamID the [SteamID].
     * @return The relationship of the friend to the local user, or null of not set.
     */
    fun getFriendRelationship(steamID: SteamID): EFriendRelationship? {
        return cache.getUser(steamID).relationship
    }

    /**
     * Gets the game name of a friend playing a game.
     * @param steamID the [SteamID].
     * @return The game name of a friend playing a game, or null if they haven't been cached yet.
     */
    fun getFriendGamePlayedName(steamID: SteamID): String? {
        return cache.getUser(steamID).gamaName
    }

    /**
     * Gets the GameID of a friend playing a game.
     * @param steamID the [SteamID].
     * @return The [GameID] of a friend playing a game, or 0 if they haven't been cached yet.
     */
    fun getFriendGamePlayed(steamID: SteamID): GameID {
        return cache.getUser(steamID).gameID
    }

    /**
     * Gets an SHA-1 hash representing the friend's avatar.
     * @param steamID the [SteamID].
     * @return A byte array representing an SHA-1 hash of the friend's avatar, or null if not set.
     */
    fun getFriendAvatar(steamID: SteamID): ByteArray? {
        return cache.getUser(steamID).avatarHash
    }

    /**
     * Gets the count of clans the local user is a member of.
     * @return The number of clans this user is a member of.
     */
    fun getClanCount(): Int {
        return clanList.size
    }

    /**
     * Gets a clan SteamID by index.
     * @param index The index.
     * @return A valid steamid of a clan if the index is in range; otherwise a steamid representing 0.
     */
    fun getClanByIndex(index: Int): SteamID {
        if (index < 0 || index >= clanList.size) {
            return SteamID(0)
        }

        return clanList[index]
    }

    /**
     * Gets the name of a clan.
     * @param steamID The clan SteamID.
     * @return The name.
     */
    fun getClanName(steamID: SteamID): String? {
        return cache.clans.getAccount(steamID, Clan::class.java).name
    }

    /**
     * Gets the relationship of a clan.
     * @param steamID The clan SteamID.
     * @return The relationship of the clan to the local user.
     */
    fun getClanRelationship(steamID: SteamID): EClanRelationship? {
        return cache.clans.getAccount(steamID, Clan::class.java).relationship
    }

    /**
     * Gets an SHA-1 hash representing the clan's avatar.
     * @param steamID The SteamID of the clan to get the avatar of.
     * @return A byte array representing an SHA-1 hash of the clan's avatar, or null if the clan could not be found.
     */
    fun getClanAvatar(steamID: SteamID): ByteArray? {
        return cache.clans.getAccount(steamID, Clan::class.java).avatarHash
    }

    /**
     * Sends a chat message to a friend.
     * @param target  The target to send to.
     * @param type    The type of message to send.
     * @param message The message to send.
     */
    fun sendChatMessage(target: SteamID, type: EChatEntryType, message: String) {
        ClientMsgProtobuf<CMsgClientFriendMsg.Builder>(
            CMsgClientFriendMsg::class.java,
            EMsg.ClientFriendMsg
        ).apply {
            body.setSteamid(target.convertToUInt64())
            body.setChatEntryType(type.code())
            body.setMessage(ByteString.copyFrom(message, StandardCharsets.UTF_8))
        }.also(client::send)
    }

    /**
     * Sends a friend request to a user.
     * @param accountNameOrEmail The account name or email of the user.
     */
    fun addFriend(accountNameOrEmail: String) {
        ClientMsgProtobuf<CMsgClientAddFriend.Builder>(
            CMsgClientAddFriend::class.java,
            EMsg.ClientAddFriend
        ).apply {
            body.setAccountnameOrEmailToAdd(accountNameOrEmail)
        }.also(client::send)
    }

    /**
     * Sends a friend request to a user.
     * @param steamID The SteamID of the friend to add.
     */
    fun addFriend(steamID: SteamID) {
        ClientMsgProtobuf<CMsgClientAddFriend.Builder>(
            CMsgClientAddFriend::class.java,
            EMsg.ClientAddFriend
        ).apply {
            body.setSteamidToAdd(steamID.convertToUInt64())
        }.also(client::send)
    }

    /**
     * Removes a friend from your friends list.
     * @param steamID The SteamID of the friend to remove.
     */
    fun removeFriend(steamID: SteamID) {
        ClientMsgProtobuf<CMsgClientRemoveFriend.Builder>(
            CMsgClientRemoveFriend::class.java,
            EMsg.ClientRemoveFriend
        ).apply {
            body.setFriendid(steamID.convertToUInt64())
        }.also(client::send)
    }

    /**
     * Attempts to join a chat room.
     * @param steamID The SteamID of the chat room.
     */
    fun joinChat(steamID: SteamID) {
        var chatId = SteamID(steamID.convertToUInt64()) // copy the steamid so we don't modify it
        if (chatId.isClanAccount) {
            chatId = chatId.toChatID()
        }

        ClientMsg(MsgClientJoinChat::class.java).apply {
            body.steamIdChat = chatId
        }.also(client::send)
    }

    /**
     * Attempts to leave a chat room.
     * @param steamID The SteamID of the chat room.
     */
    fun leaveChat(steamID: SteamID) {
        val chatId = SteamID(steamID.convertToUInt64()) // copy the steamid so we don't modify it
        if (chatId.isClanAccount) {
            // this steamid is incorrect, so we'll fix it up
            chatId.accountInstance = ChatInstanceFlags.CLAN.code
            chatId.accountType = EAccountType.Chat
        }

        ClientMsg(MsgClientChatMemberInfo::class.java).apply {
            body.steamIdChat = chatId
            body.type = EChatInfoType.StateChange
            try {
                // SteamID can be null if not connected - will be ultimately ignored in Client.Send.
                val localSteamID = client.steamID?.convertToUInt64() ?: return

                write(localSteamID) // ChatterActedOn
                write(EChatMemberStateChange.Left.code()) // StateChange
                write(localSteamID) // ChatterActedBy
            } catch (e: IOException) {
                logger.debug(e)
            }
        }.also(client::send)
    }

    /**
     * Sends a message to a chat room.
     * @param steamIdChat The SteamID of the chat room.
     * @param type        The message type.
     * @param message     The message.
     */
    fun sendChatRoomMessage(steamIdChat: SteamID, type: EChatEntryType, message: String) {
        val chatId = SteamID(steamIdChat.convertToUInt64()) // copy the steamid so we don't modify it
        if (chatId.isClanAccount) {
            // this steamid is incorrect, so we'll fix it up
            chatId.accountInstance = ChatInstanceFlags.CLAN.code
            chatId.accountType = EAccountType.Chat
        }

        ClientMsg(MsgClientChatMsg::class.java).apply {
            body.chatMsgType = type
            body.steamIdChatRoom = chatId
            // Can be null if not connected - will ultimately be ignored in Client.Send.
            body.steamIdChatter = client.steamID ?: SteamID()
            try {
                writeNullTermString(message, StandardCharsets.UTF_8)
            } catch (e: IOException) {
                logger.debug(e)
            }
        }.also(client::send)
    }

    /**
     * Invites a user to a chat room.
     *  The results of this action will be available through the [ChatActionResultCallback] callback.
     * @param steamIdUser The SteamID of the user to invite.
     * @param steamIdChat The SteamID of the chat room to invite the user to.
     */
    fun inviteUserToChat(steamIdUser: SteamID, steamIdChat: SteamID) {
        val chatId = SteamID(steamIdChat.convertToUInt64()) // copy the steamid so we don't modify it
        if (chatId.isClanAccount) {
            // this steamid is incorrect, so we'll fix it up
            chatId.accountInstance = ChatInstanceFlags.CLAN.code
            chatId.accountType = EAccountType.Chat
        }

        ClientMsgProtobuf<CMsgClientChatInvite.Builder>(
            CMsgClientChatInvite::class.java,
            EMsg.ClientChatInvite
        ).apply {
            body.setSteamIdChat(chatId.convertToUInt64())
            body.setSteamIdInvited(steamIdUser.convertToUInt64())
            // steamclient also sends the steamid of the user that did the invitation
            // we'll mimic that behavior
            body.setSteamIdPatron(client.steamID?.convertToUInt64() ?: SteamID().convertToUInt64())
        }.also(client::send)
    }

    /**
     * Kicks the specified chat member from the given chat room.
     * @param steamIdChat   The SteamID of chat room to kick the member from.
     * @param steamIdMember The SteamID of the member to kick from the chat.
     */
    fun kickChatMember(steamIdChat: SteamID, steamIdMember: SteamID) {
        val chatId = SteamID(steamIdChat.convertToUInt64()) // copy the steamid so we don't modify it
        if (chatId.isClanAccount) {
            // this steamid is incorrect, so we'll fix it up
            chatId.accountInstance = ChatInstanceFlags.CLAN.code
            chatId.accountType = EAccountType.Chat
        }

        ClientMsg(MsgClientChatAction::class.java).apply {
            body.steamIdChat = chatId
            body.steamIdUserToActOn = steamIdMember
            body.chatAction = EChatAction.Kick
        }.also(client::send)
    }

    /**
     * Bans the specified chat member from the given chat room.
     * @param steamIdChat   The SteamID of chat room to ban the member from.
     * @param steamIdMember The SteamID of the member to ban from the chat.
     */
    fun banChatMember(steamIdChat: SteamID, steamIdMember: SteamID) {
        val chatId = SteamID(steamIdChat.convertToUInt64()) // copy the steamid so we don't modify it
        if (chatId.isClanAccount) {
            // this steamid is incorrect, so we'll fix it up
            chatId.accountInstance = ChatInstanceFlags.CLAN.code
            chatId.accountType = EAccountType.Chat
        }

        ClientMsg(MsgClientChatAction::class.java).apply {
            body.steamIdChat = chatId
            body.steamIdUserToActOn = steamIdMember
            body.chatAction = EChatAction.Ban
        }.also(client::send)
    }

    /**
     * Unbans the specified chat member from the given chat room.
     * @param steamIdChat   The SteamID of chat room to unban the member from.
     * @param steamIdMember The SteamID of the member to unban from the chat.
     */
    fun unbanChatMember(steamIdChat: SteamID, steamIdMember: SteamID) {
        val chatId = SteamID(steamIdChat.convertToUInt64()) // copy the steamid so we don't modify it
        if (chatId.isClanAccount) {
            // this steamid is incorrect, so we'll fix it up
            chatId.accountInstance = ChatInstanceFlags.CLAN.code
            chatId.accountType = EAccountType.Chat
        }

        ClientMsg(MsgClientChatAction::class.java).apply {
            body.steamIdChat = chatId
            body.steamIdUserToActOn = steamIdMember
            body.chatAction = EChatAction.UnBan
        }.also(client::send)
    }

    /**
     * Requests persona state for a list of specified SteamID.
     *  Results are returned in [PersonaState].
     * @param steamIdList   A list of SteamIDs to request the info of.
     * @param requestedInfo The requested info flags. If none specified, this uses [SteamConfiguration.defaultPersonaStateFlags].
     */
    @JvmOverloads
    fun requestFriendInfo(steamIdList: List<SteamID>, requestedInfo: Int? = null) {
        var info = requestedInfo

        if (info == null) {
            info = EClientPersonaStateFlag.code(client.configuration.defaultPersonaStateFlags)
        }

        ClientMsgProtobuf<CMsgClientRequestFriendData.Builder>(
            CMsgClientRequestFriendData::class.java,
            EMsg.ClientRequestFriendData
        ).apply {
            body.setPersonaStateRequested(info)
            body.addAllFriends(steamIdList.map { it.convertToUInt64() })
        }.also(client::send)
    }

    /**
     * Requests persona state for a specified SteamID.
     *  Results are returned in [PersonaState].
     * @param steamID       A SteamID to request the info of.
     * @param requestedInfo The requested info flags. If none specified, this uses [SteamConfiguration.defaultPersonaStateFlags].
     */
    @JvmOverloads
    fun requestFriendInfo(steamID: SteamID, requestedInfo: Int? = null) {
        requestFriendInfo(listOf(steamID), requestedInfo)
    }

    /**
     * Ignores or Un-Ignores a friend on Steam.
     *  Results are returned in a [IgnoreFriendCallback].
     * @param steamID   The SteamID of the friend to ignore or un-ignore.
     * @param setIgnore if set to **true**, the friend will be ignored; otherwise, they will be un-ignored.
     * @return The Job ID of the request. This can be used to find the appropriate [IgnoreFriendCallback].
     */
    @JvmOverloads
    fun ignoreFriend(steamID: SteamID, setIgnore: Boolean = true): AsyncJobSingle<IgnoreFriendCallback> {
        val ignore = ClientMsg(MsgClientSetIgnoreFriend::class.java).apply {
            sourceJobID = client.getNextJobID()
            body.mySteamId =
                client.steamID ?: SteamID() // Can be null if not connected - will ultimately be ignored in Client.Send.
            body.ignore = if (setIgnore) 1.toByte() else 0.toByte()
            body.steamIdFriend = steamID
        }.also(client::send)

        return AsyncJobSingle(client, ignore.sourceJobID)
    }

    /**
     * Requests profile information for the given [SteamID]
     *  Results are returned in a [ProfileInfoCallback]
     * @param steamID The SteamID of the friend to request the details of.
     * @return The Job ID of the request. This can be used to find the appropriate [ProfileInfoCallback].
     */
    fun requestProfileInfo(steamID: SteamID): AsyncJobSingle<ProfileInfoCallback> {
        val request = ClientMsgProtobuf<CMsgClientFriendProfileInfo.Builder>(
            CMsgClientFriendProfileInfo::class.java,
            EMsg.ClientFriendProfileInfo
        ).apply {
            sourceJobID = client.getNextJobID()
            body.setSteamidFriend(steamID.convertToUInt64())
        }.also(client::send)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    /**
     * Requests the last few chat messages with a friend.
     *  Results are returned in a [FriendMsgHistoryCallback]
     * @param steamID SteamID of the friend
     */
    fun requestMessageHistory(steamID: SteamID) {
        ClientMsgProtobuf<CMsgClientChatGetFriendMessageHistory.Builder>(
            CMsgClientChatGetFriendMessageHistory::class.java,
            EMsg.ClientChatGetFriendMessageHistory
        ).apply {
            body.setSteamid(steamID.convertToUInt64())
        }.also(client::send)
    }

    /**
     * Requests all offline messages.
     *  This also marks them as read server side.
     *  Results are returned in a [FriendMsgHistoryCallback].
     */
    fun requestOfflineMessages() {
        ClientMsgProtobuf<CMsgClientChatGetFriendMessageHistoryForOfflineMessages.Builder>(
            CMsgClientChatGetFriendMessageHistoryForOfflineMessages::class.java,
            EMsg.ClientChatGetFriendMessageHistoryForOfflineMessages
        ).also(client::send)
    }

    /**
     * Set the nickname of a friend.
     *  The result is returned in a [NicknameCallback].
     * @param friendID the steam id of the friend
     * @param nickname the nickname to set to
     * @return The Job ID of the request. This can be used to find the appropriate [NicknameCallback].
     */
    fun setFriendNickname(friendID: SteamID, nickname: String): JobID {
        val jobID: JobID = client.getNextJobID()
        ClientMsgProtobuf<CMsgClientSetPlayerNickname.Builder>(
            CMsgClientSetPlayerNickname::class.java,
            EMsg.AMClientSetPlayerNickname
        ).apply {
            sourceJobID = jobID
            body.setSteamid(friendID.convertToUInt64())
            body.setNickname(nickname)
        }.also(client::send)

        return jobID
    }

    /**
     * Request the alias history of the account of the given steam id.
     *  The result is returned in a [AliasHistoryCallback].
     * @param steamID the steam id
     * @return The Job ID of the request. This can be used to find the appropriate [AliasHistoryCallback].
     */
    fun requestAliasHistory(steamID: SteamID): JobID = requestAliasHistory(listOf(steamID))

    /**
     * Request the alias history of the accounts of the given steam ids.
     *  The result is returned in a [AliasHistoryCallback].
     * @param steamIDs the steam ids
     * @return The Job ID of the request. This can be used to find the appropriate [AliasHistoryCallback].
     */
    fun requestAliasHistory(steamIDs: List<SteamID>): JobID {
        val jobID: JobID = client.getNextJobID()
        ClientMsgProtobuf<CMsgClientAMGetPersonaNameHistory.Builder>(
            CMsgClientAMGetPersonaNameHistory::class.java,
            EMsg.ClientAMGetPersonaNameHistory
        ).apply {
            sourceJobID = jobID
            body.setIdCount(steamIDs.size) // Should count the list size instead of the body size from initial commit.
            steamIDs.forEach { steamID ->
                val id = CMsgClientAMGetPersonaNameHistory.IdInstance.newBuilder().setSteamid(steamID.convertToUInt64())
                body.addIds(id)
            }
        }.also(client::send)

        return jobID
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleAccountInfo(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientAccountInfo.Builder>(
            CMsgClientAccountInfo::class.java,
            packetMsg.msgType
        ).also {
            // cache off our local name
            cache.localUser.name = it.body.personaName
        }
    }

    private fun handleFriendMsg(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientFriendMsgIncoming.Builder>(
            CMsgClientFriendMsgIncoming::class.java,
            packetMsg
        ).also { resp ->
            FriendMsgCallback(resp.body).also(client::postCallback)
        }
    }

    private fun handleFriendEchoMsg(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientFriendMsgIncoming.Builder>(
            CMsgClientFriendMsgIncoming::class.java,
            packetMsg
        ).also { resp ->
            FriendMsgEchoCallback(resp.body).also(client::postCallback)
        }
    }

    private fun handleFriendMessageHistoryResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientChatGetFriendMessageHistoryResponse.Builder>(
            CMsgClientChatGetFriendMessageHistoryResponse::class.java,
            packetMsg
        ).also { resp ->
            FriendMsgHistoryCallback(resp.body, client.universe).also(client::postCallback)
        }
    }

    private fun handleFriendsList(packetMsg: IPacketMsg) {
        val list = ClientMsgProtobuf<CMsgClientFriendsList.Builder>(CMsgClientFriendsList::class.java, packetMsg)

        cache.localUser.steamID = client.steamID!!

        // should lock?
        if (!list.body.bincremental) {
            // if we're not an incremental update, the message contains all friends, so we should clear our current list
            friendList.clear()
            clanList.clear()
        }

        // we have to request information for all of our friends because steam only sends persona information for online friends
        val reqInfo = ClientMsgProtobuf<CMsgClientRequestFriendData.Builder>(
            CMsgClientRequestFriendData::class.java,
            EMsg.ClientRequestFriendData
        ).apply {
            body.setPersonaStateRequested(EClientPersonaStateFlag.code(client.configuration.defaultPersonaStateFlags))
        }

        // should lock?
        val friendsToRemove = mutableListOf<SteamID>()
        val clansToRemove = mutableListOf<SteamID>()

        list.body.friendsList.forEach { friendObj ->
            val friendId = SteamID(friendObj.ulfriendid)

            if (friendId.isIndividualAccount) {
                val user = cache.getUser(friendId).apply {
                    relationship = EFriendRelationship.from(friendObj.efriendrelationship)
                }

                if (friendList.contains(friendId)) {
                    // if this is a friend on our list, and they removed us, mark them for removal
                    if (user.relationship == EFriendRelationship.None) {
                        friendsToRemove.add(friendId)
                    }
                } else {
                    // we don't know about this friend yet, lets add them
                    friendList.add(friendId)
                }
            } else if (friendId.isClanAccount) {
                val clan = cache.clans.getAccount(friendId, Clan::class.java).apply {
                    relationship = EClanRelationship.from(friendObj.efriendrelationship)
                }

                if (clanList.contains(friendId)) {
                    // mark clans we were removed/kicked from
                    // note: not actually sure about the kicked relationship, but I'm using it for good measure
                    if (clan.relationship == EClanRelationship.None || clan.relationship == EClanRelationship.Kicked) {
                        clansToRemove.add(friendId)
                    }
                } else {
                    // don't know about this clan, add it
                    clanList.add(friendId)
                }
            }

            if (!list.body.bincremental) {
                // request persona state for our friend & clan list when it's a non-incremental update
                reqInfo.body.addFriends(friendId.convertToUInt64())
            }
        }

        // remove anything we marked for removal
        friendsToRemove.forEach { friendList.remove(it) }
        clansToRemove.forEach { clanList.remove(it) }

        if (reqInfo.body.friendsCount > 0) {
            reqInfo.also(client::send)
        }

        FriendsListCallback(list.body).also(client::postCallback)
    }

    private fun handlePersonaState(packetMsg: IPacketMsg) {
        val perState = ClientMsgProtobuf<CMsgClientPersonaState.Builder>(
            CMsgClientPersonaState::class.java,
            packetMsg
        )

        val flags = EClientPersonaStateFlag.from(perState.body.statusFlags)

        perState.body.friendsList.forEach { friend ->
            val friendId = SteamID(friend.friendid)

            if (friendId.isIndividualAccount) {
                val cacheFriend = cache.getUser(friendId)

                // TODO check this
                if (EClientPersonaStateFlag.from(EClientPersonaStateFlag.code(flags))
                        .contains(EClientPersonaStateFlag.PlayerName)
                ) {
                    cacheFriend.name = friend.playerName
                }

                // TODO check this
                if (EClientPersonaStateFlag.from(EClientPersonaStateFlag.code(flags))
                        .contains(EClientPersonaStateFlag.Presence)
                ) {
                    cacheFriend.avatarHash = friend.avatarHash.toByteArray()
                    cacheFriend.personaState = EPersonaState.from(friend.personaState)
                    cacheFriend.personaStateFlags = EPersonaStateFlag.from(friend.personaStateFlags)
                }

                // TODO check this
                if (EClientPersonaStateFlag.from(EClientPersonaStateFlag.code(flags))
                        .contains(EClientPersonaStateFlag.GameDataBlob)
                ) {
                    cacheFriend.gamaName = friend.gameName
                    cacheFriend.gameID = GameID(friend.gameid)
                    cacheFriend.gameAppID = friend.gamePlayedAppId
                }
            } else if (friendId.isClanAccount) {
                val cacheClan = cache.clans.getAccount(friendId, Clan::class.java)

                // TODO check this
                if (EClientPersonaStateFlag.from(EClientPersonaStateFlag.code(flags))
                        .contains(EClientPersonaStateFlag.PlayerName)
                ) {
                    cacheClan.name = friend.playerName
                }

                // TODO check this
                if (EClientPersonaStateFlag.from(EClientPersonaStateFlag.code(flags))
                        .contains(EClientPersonaStateFlag.Presence)
                ) {
                    cacheClan.avatarHash = friend.avatarHash.toByteArray()
                }
            }

            // todo: cache other details/account types?
        }

        PersonaStatesCallback(perState.body).also(client::postCallback)
    }

    private fun handleClanState(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientClanState.Builder>(
            CMsgClientClanState::class.java,
            packetMsg
        ).also { resp ->
            ClanStateCallback(resp.body).also(client::postCallback)
        }
    }

    private fun handleFriendResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientAddFriendResponse.Builder>(
            CMsgClientAddFriendResponse::class.java,
            packetMsg
        ).also { resp ->
            FriendAddedCallback(resp.body).also(client::postCallback)
        }
    }

    private fun handleChatEnter(packetMsg: IPacketMsg) {
        ClientMsg(
            MsgClientChatEnter::class.java,
            packetMsg
        ).also { resp ->
            ChatEnterCallback(resp.body, resp.payload.toByteArray()).also(client::postCallback)
        }
    }

    private fun handleChatMsg(packetMsg: IPacketMsg) {
        ClientMsg(
            MsgClientChatMsg::class.java,
            packetMsg
        ).also { resp ->
            ChatMsgCallback(resp.body, resp.payload.toByteArray()).also(client::postCallback)
        }
    }

    private fun handleChatMemberInfo(packetMsg: IPacketMsg) {
        ClientMsg(
            MsgClientChatMemberInfo::class.java,
            packetMsg
        ).also { resp ->
            ChatMemberInfoCallback(resp.body, resp.payload.toByteArray()).also(client::postCallback)
        }
    }

    private fun handleChatRoomInfo(packetMsg: IPacketMsg) {
        ClientMsg(
            MsgClientChatRoomInfo::class.java,
            packetMsg
        ).also { resp ->
            ChatRoomInfoCallback(resp.body, resp.payload.toByteArray()).also(client::postCallback)
        }
    }

    private fun handleChatActionResult(packetMsg: IPacketMsg) {
        ClientMsg(
            MsgClientChatActionResult::class.java,
            packetMsg
        ).also { resp ->
            ChatActionResultCallback(resp.body).also(client::postCallback)
        }
    }

    private fun handleChatInvite(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientChatInvite.Builder>(
            CMsgClientChatInvite::class.java,
            packetMsg
        ).also { resp ->
            ChatInviteCallback(resp.body).also(client::postCallback)
        }
    }

    private fun handleIgnoreFriendResponse(packetMsg: IPacketMsg) {
        ClientMsg(
            MsgClientSetIgnoreFriendResponse::class.java,
            packetMsg
        ).also { resp ->
            IgnoreFriendCallback(resp.targetJobID, resp.body).also(client::postCallback)
        }
    }

    private fun handleProfileInfoResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientFriendProfileInfoResponse.Builder>(
            CMsgClientFriendProfileInfoResponse::class.java,
            packetMsg
        ).also { resp ->
            ProfileInfoCallback(JobID(packetMsg.targetJobID), resp.body).also(client::postCallback)
        }
    }

    private fun handlePersonaChangeResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgPersonaChangeResponse.Builder>(
            CMsgPersonaChangeResponse::class.java,
            packetMsg
        ).also { resp ->
            // update our cache to what steam says our name is
            cache.localUser.name = resp.body.playerName

            PersonaChangeCallback(JobID(packetMsg.targetJobID), resp.body).also(client::postCallback)
        }
    }

    private fun handleNicknameList(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientPlayerNicknameList.Builder>(
            CMsgClientPlayerNicknameList::class.java,
            packetMsg
        ).also { resp ->
            NicknameListCallback(resp.body).also(client::postCallback)
        }
    }

    private fun handlePlayerNicknameResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientSetPlayerNicknameResponse.Builder>(
            CMsgClientSetPlayerNicknameResponse::class.java,
            packetMsg
        ).also { resp ->
            NicknameCallback(resp.targetJobID, resp.body).also(client::postCallback)
        }
    }

    private fun handleAliasHistoryResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientAMGetPersonaNameHistoryResponse.Builder>(
            CMsgClientAMGetPersonaNameHistoryResponse::class.java,
            packetMsg
        ).also { resp ->
            AliasHistoryCallback(resp.targetJobID, resp.body).also(client::postCallback)
        }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(SteamFriends::class.java)
    }
}
