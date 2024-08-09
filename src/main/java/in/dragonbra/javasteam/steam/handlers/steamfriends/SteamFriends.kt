package `in`.dragonbra.javasteam.steam.handlers.steamfriends

import com.google.protobuf.ByteString
import `in`.dragonbra.javasteam.base.ClientMsg
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.*
import `in`.dragonbra.javasteam.generated.*
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.*
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.*
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.*
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.*
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * This handler handles all interaction with other users on the Steam3 network.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class SteamFriends : ClientMsgHandler() {

    /**
     * Sets the local user's persona name and broadcasts it over the network.
     * Results are returned in a[PersonaChangeCallback] callback.
     *
     * @param name The name.
     */
    fun setPersonaName(name: String) {
        val stateMsg = ClientMsgProtobuf<CMsgClientChangeStatus.Builder>(
            CMsgClientChangeStatus::class.java,
            EMsg.ClientChangeStatus
        )

        stateMsg.body.setPlayerName(name)

        client.send(stateMsg)
    }

    /**
     * Sets the local user's persona state and broadcasts it over the network.
     * Results are returned in a[PersonaChangeCallback] callback.
     *
     * @param state The state.
     */
    fun setPersonaState(state: EPersonaState) {
        val stateMsg = ClientMsgProtobuf<CMsgClientChangeStatus.Builder>(
            CMsgClientChangeStatus::class.java,
            EMsg.ClientChangeStatus
        )

        stateMsg.body.setPersonaState(state.code())
        stateMsg.body.setPersonaSetByUser(true)

        client.send(stateMsg)
    }

    /**
     * JavaSteam addition:
     * Sets the local user's persona state flag back to normal desktop mode.
     */
    fun resetPersonaStateFlag() {
        val stateMsg = ClientMsgProtobuf<CMsgClientChangeStatus.Builder>(
            CMsgClientChangeStatus::class.java,
            EMsg.ClientChangeStatus
        )

        stateMsg.body.setPersonaSetByUser(true)
        stateMsg.body.setPersonaStateFlags(0)

        client.send(stateMsg)
    }

    /**
     * JavaSteam addition:
     * Sets the local user's persona state flag to a valid ClientType
     *
     * @param flag one of the following
     * [EPersonaStateFlag.ClientTypeWeb],
     * [EPersonaStateFlag.ClientTypeMobile],
     * [EPersonaStateFlag.ClientTypeTenfoot],
     * or [EPersonaStateFlag.ClientTypeVR].
     */
    fun setPersonaStateFlag(flag: EPersonaStateFlag) {
        require(!(flag.code() < EPersonaStateFlag.ClientTypeWeb.code() || flag.code() > EPersonaStateFlag.ClientTypeVR.code())) { "Persona State Flag was not a valid ClientType" }

        val stateMsg = ClientMsgProtobuf<CMsgClientChangeStatus.Builder>(
            CMsgClientChangeStatus::class.java,
            EMsg.ClientChangeStatus
        )

        stateMsg.body.setPersonaSetByUser(true)
        stateMsg.body.setPersonaStateFlags(flag.code())

        client.send(stateMsg)
    }

    /**
     * Sends a chat message to a friend.
     *
     * @param target  The target to send to.
     * @param type    The type of message to send.
     * @param message The message to send.
     */
    fun sendChatMessage(target: SteamID, type: EChatEntryType, message: String) {
        val chatMsg = ClientMsgProtobuf<CMsgClientFriendMsg.Builder>(
            CMsgClientFriendMsg::class.java,
            EMsg.ClientFriendMsg
        )

        chatMsg.body.setSteamid(target.convertToUInt64())
        chatMsg.body.setChatEntryType(type.code())
        chatMsg.body.setMessage(ByteString.copyFrom(message, StandardCharsets.UTF_8))

        client.send(chatMsg)
    }

    /**
     * Sends a friend request to a user.
     *
     * @param accountNameOrEmail The account name or email of the user.
     */
    fun addFriend(accountNameOrEmail: String) {
        val addFriend = ClientMsgProtobuf<CMsgClientAddFriend.Builder>(
            CMsgClientAddFriend::class.java,
            EMsg.ClientAddFriend
        )

        addFriend.body.setAccountnameOrEmailToAdd(accountNameOrEmail)

        client.send(addFriend)
    }

    /**
     * Sends a friend request to a user.
     *
     * @param steamID The SteamID of the friend to add.
     */
    fun addFriend(steamID: SteamID) {
        val addFriend = ClientMsgProtobuf<CMsgClientAddFriend.Builder>(
            CMsgClientAddFriend::class.java,
            EMsg.ClientAddFriend
        )

        addFriend.body.setSteamidToAdd(steamID.convertToUInt64())

        client.send(addFriend)
    }

    /**
     * Removes a friend from your friends list.
     *
     * @param steamID The SteamID of the friend to remove.
     */
    fun removeFriend(steamID: SteamID) {
        val removeFriend = ClientMsgProtobuf<CMsgClientRemoveFriend.Builder>(
            CMsgClientRemoveFriend::class.java,
            EMsg.ClientRemoveFriend
        )

        removeFriend.body.setFriendid(steamID.convertToUInt64())

        client.send(removeFriend)
    }

    /**
     * Attempts to join a chat room.
     *
     * @param steamID The SteamID of the chat room.
     */
    fun joinChat(steamID: SteamID) {
        val chatID: SteamID = fixChatID(steamID) // copy the steamid so we don't modify it

        val joinChat = ClientMsg(MsgClientJoinChat::class.java)

        joinChat.body.steamIdChat = chatID

        client.send(joinChat)
    }

    /**
     * Attempts to leave a chat room.
     *
     * @param steamID The SteamID of the chat room.
     */
    fun leaveChat(steamID: SteamID) {
        val chatID: SteamID = fixChatID(steamID) // copy the steamid so we don't modify it

        val leaveChat = ClientMsg(MsgClientChatMemberInfo::class.java)

        leaveChat.body.steamIdChat = chatID
        leaveChat.body.type = EChatInfoType.StateChange

        try {
            leaveChat.write(client.steamID.convertToUInt64()) // ChatterActedOn
            leaveChat.write(EChatMemberStateChange.Left.code()) // StateChange
            leaveChat.write(client.steamID.convertToUInt64()) // ChatterActedBy
        } catch (e: IOException) {
            logger.debug(e)
        }

        client.send(leaveChat)
    }

    /**
     * Sends a message to a chat room.
     *
     * @param steamIdChat The SteamID of the chat room.
     * @param type        The message type.
     * @param message     The message.
     */
    fun sendChatRoomMessage(steamIdChat: SteamID, type: EChatEntryType, message: String) {
        val chatID: SteamID = fixChatID(steamIdChat) // copy the steamid so we don't modify it

        val chatMsg = ClientMsg(MsgClientChatMsg::class.java)

        chatMsg.body.chatMsgType = type
        chatMsg.body.steamIdChatRoom = chatID
        chatMsg.body.steamIdChatter = client.steamID

        try {
            chatMsg.writeNullTermString(message, StandardCharsets.UTF_8)
        } catch (e: IOException) {
            logger.debug(e)
        }

        client.send(chatMsg)
    }

    /**
     * Invites a user to a chat room.
     * The results of this action will be available through the [ChatActionResultCallback] callback.
     *
     * @param steamIdUser The SteamID of the user to invite.
     * @param steamIdChat The SteamID of the chat room to invite the user to.
     */
    fun inviteUserToChat(steamIdUser: SteamID, steamIdChat: SteamID) {
        val chatID: SteamID = fixChatID(steamIdChat) // copy the steamid so we don't modify it

        val inviteMsg = ClientMsgProtobuf<CMsgClientChatInvite.Builder>(
            CMsgClientChatInvite::class.java,
            EMsg.ClientChatInvite
        )

        inviteMsg.body.setSteamIdChat(chatID.convertToUInt64())
        inviteMsg.body.setSteamIdInvited(steamIdUser.convertToUInt64())

        // steamclient also sends the steamid of the user that did the invitation
        // we'll mimic that behavior
        inviteMsg.body.setSteamIdPatron(client.getSteamID().convertToUInt64())

        client.send(inviteMsg)
    }

    /**
     * Kicks the specified chat member from the given chat room.
     *
     * @param steamIdChat   The SteamID of chat room to kick the member from.
     * @param steamIdMember The SteamID of the member to kick from the chat.
     */
    fun kickChatMember(steamIdChat: SteamID, steamIdMember: SteamID) {
        val chatID: SteamID = fixChatID(steamIdChat) // copy the steamid so we don't modify it

        val kickMember = ClientMsg(MsgClientChatAction::class.java)

        kickMember.body.steamIdChat = chatID
        kickMember.body.steamIdUserToActOn = steamIdMember

        kickMember.body.chatAction = EChatAction.Kick

        client.send(kickMember)
    }

    /**
     * Bans the specified chat member from the given chat room.
     *
     * @param steamIdChat   The SteamID of chat room to ban the member from.
     * @param steamIdMember The SteamID of the member to ban from the chat.
     */
    fun banChatMember(steamIdChat: SteamID, steamIdMember: SteamID) {
        val chatID: SteamID = fixChatID(steamIdChat) // copy the steamid so we don't modify it

        val kickMember = ClientMsg(MsgClientChatAction::class.java)

        kickMember.body.steamIdChat = chatID
        kickMember.body.steamIdUserToActOn = steamIdMember

        kickMember.body.chatAction = EChatAction.Ban

        client.send(kickMember)
    }

    /**
     * Unbans the specified chat member from the given chat room.
     *
     * @param steamIdChat   The SteamID of chat room to unban the member from.
     * @param steamIdMember The SteamID of the member to unban from the chat.
     */
    fun unbanChatMember(steamIdChat: SteamID, steamIdMember: SteamID) {
        val chatID: SteamID = fixChatID(steamIdChat) // copy the steamid so we don't modify it

        val kickMember = ClientMsg(MsgClientChatAction::class.java)

        kickMember.body.steamIdChat = chatID
        kickMember.body.steamIdUserToActOn = steamIdMember

        kickMember.body.chatAction = EChatAction.UnBan

        client.send(kickMember)
    }

    /**
     * Requests persona state for a list of specified SteamID.
     * Results are returned in [PersonaState].
     *
     * @param steamIdList   A list of SteamIDs to request the info of.
     * @param requestedInfo The requested info flags. If none specified, this uses [SteamConfiguration.getDefaultPersonaStateFlags].
     */
    fun requestFriendInfo(steamIdList: List<SteamID>, requestedInfo: Int) {
        var info = requestedInfo

        if (info == 0) {
            info = EClientPersonaStateFlag.code(client.configuration.defaultPersonaStateFlags)
        }

        val request = ClientMsgProtobuf<CMsgClientRequestFriendData.Builder>(
            CMsgClientRequestFriendData::class.java,
            EMsg.ClientRequestFriendData
        )

        for (steamID in steamIdList) {
            request.body.addFriends(steamID.convertToUInt64())
        }

        request.body.setPersonaStateRequested(info)

        client.send(request)
    }

    /**
     * Requests persona state for a specified SteamID.
     * Results are returned in [PersonaState].
     *
     * @param steamID A SteamID to request the info of.
     * @param requestedInfo The requested info flags. If none specified, this uses [SteamConfiguration.getDefaultPersonaStateFlags].
     */
    fun requestFriendInfo(steamID: SteamID, requestedInfo: Int) {
        val list: List<SteamID> = listOf(steamID)
        requestFriendInfo(list, requestedInfo)
    }

    /**
     * Ignores or un-ignores a friend on Steam.
     * Results are returned in a [IgnoreFriendCallback].
     *
     * @param steamID The SteamID of the friend to ignore or un-ignore.
     * @param setIgnore if set to **true**, the friend will be ignored; otherwise, they will be un-ignored.
     * @return The Job ID of the request. This can be used to find the appropriate [IgnoreFriendCallback].
     */
    @JvmOverloads
    fun ignoreFriend(steamID: SteamID, setIgnore: Boolean = true): AsyncJobSingle<IgnoreFriendCallback> {
        val ignore = ClientMsg(MsgClientSetIgnoreFriend::class.java)
        val jobID: JobID = client.getNextJobID()

        ignore.setSourceJobID(jobID)

        ignore.body.mySteamId = client.steamID
        ignore.body.ignore = if (setIgnore) 1.toByte() else 0.toByte()
        ignore.body.steamIdFriend = steamID

        client.send(ignore)

        return AsyncJobSingle(client, ignore.sourceJobID)
    }

    /**
     * Requests profile information for the given [SteamID]
     * Results are returned in a [ProfileInfoCallback]
     *
     * @param steamID The SteamID of the friend to request the details of.
     * @return The Job ID of the request. This can be used to find the appropriate [ProfileInfoCallback].
     */
    fun requestProfileInfo(steamID: SteamID): AsyncJobSingle<ProfileInfoCallback> {
        val request = ClientMsgProtobuf<CMsgClientFriendProfileInfo.Builder>(
            CMsgClientFriendProfileInfo::class.java,
            EMsg.ClientFriendProfileInfo
        )
        val jobID: JobID = client.getNextJobID()

        request.setSourceJobID(jobID)

        request.body.setSteamidFriend(steamID.convertToUInt64())

        client.send(request)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    /**
     * Requests the last few chat messages with a friend.
     * Results are returned in a [FriendMsgHistoryCallback]
     *
     * @param steamID SteamID of the friend
     */
    fun requestMessageHistory(steamID: SteamID) {
        val request = ClientMsgProtobuf<CMsgClientChatGetFriendMessageHistory.Builder>(
            CMsgClientChatGetFriendMessageHistory::class.java,
            EMsg.ClientChatGetFriendMessageHistory
        )

        request.body.setSteamid(steamID.convertToUInt64())

        client.send(request)
    }

    /**
     * Requests all offline messages.
     * This also marks them as read server side.
     * Results are returned in a [FriendMsgHistoryCallback].
     */
    fun requestOfflineMessages() {
        val request = ClientMsgProtobuf<CMsgClientChatGetFriendMessageHistoryForOfflineMessages.Builder>(
            CMsgClientChatGetFriendMessageHistoryForOfflineMessages::class.java,
            EMsg.ClientChatGetFriendMessageHistoryForOfflineMessages
        )

        client.send(request)
    }

    /**
     * Set the nickname of a friend.
     * The result is returned in a [NicknameCallback].
     *
     * @param friendID the steam id of the friend
     * @param nickname the nickname to set to
     * @return The Job ID of the request. This can be used to find the appropriate [NicknameCallback].
     */
    fun setFriendNickname(friendID: SteamID, nickname: String): JobID {
        val request = ClientMsgProtobuf<CMsgClientSetPlayerNickname.Builder>(
            CMsgClientSetPlayerNickname::class.java,
            EMsg.AMClientSetPlayerNickname
        )
        val jobID: JobID = client.getNextJobID()

        request.setSourceJobID(jobID)

        request.body.setSteamid(friendID.convertToUInt64())
        request.body.setNickname(nickname)

        client.send(request)

        return jobID
    }

    /**
     * Request the alias history of the account of the given steam id.
     * The result is returned in a [AliasHistoryCallback].
     *
     * @param steamID the steam id
     * @return The Job ID of the request. This can be used to find the appropriate [AliasHistoryCallback].
     */
    fun requestAliasHistory(steamID: SteamID): JobID = requestAliasHistory(listOf(steamID))

    /**
     * Request the alias history of the accounts of the given steam ids.
     * The result is returned in a [AliasHistoryCallback].
     *
     * @param steamIDs the steam ids
     * @return The Job ID of the request. This can be used to find the appropriate [AliasHistoryCallback].
     */
    fun requestAliasHistory(steamIDs: List<SteamID>): JobID {
        val request = ClientMsgProtobuf<CMsgClientAMGetPersonaNameHistory.Builder>(
            CMsgClientAMGetPersonaNameHistory::class.java,
            EMsg.ClientAMGetPersonaNameHistory
        )
        val jobID: JobID = client.getNextJobID()

        request.setSourceJobID(jobID)

        for (steamID in steamIDs) {
            request.body.addIds(
                CMsgClientAMGetPersonaNameHistory.IdInstance.newBuilder()
                    .setSteamid(steamID.convertToUInt64())
            )
        }

        request.body.setIdCount(request.body.idsCount)

        client.send(request)

        return jobID
    }

    private fun fixChatID(steamIdChat: SteamID): SteamID {
        var chatID = SteamID(steamIdChat.convertToUInt64()) // copy the steamid so we don't modify it

        if (chatID.isClanAccount) {
            // this steamid is incorrect, so we'll fix it up
            chatID = chatID.toChatID()
        }

        return chatID
    }

    /**
     * Handles a client message. This should not be called directly.
     * @param packetMsg The packet message that contains the data.
     */
    override fun handleMsg(packetMsg: IPacketMsg) {
        val callback = getCallback(packetMsg)

        // ignore messages that we don't have a handler function for
        callback?.let {
            client.postCallback(callback)
            return
        }

        when (packetMsg.msgType) {
            EMsg.ClientPersonaState -> handlePersonaState(packetMsg)
            EMsg.ClientFriendsList -> handleFriendsList(packetMsg)
            EMsg.ClientChatGetFriendMessageHistoryResponse -> handleFriendMessageHistoryResponse(packetMsg)
            EMsg.ClientAccountInfo -> handleAccountInfo(packetMsg)
            EMsg.ClientPersonaChangeResponse -> handlePersonaChangeResponse(packetMsg)
            else -> Unit
        }
    }

    private fun handlePersonaState(packetMsg: IPacketMsg) {
        // TODO
    }

    private fun handleFriendsList(packetMsg: IPacketMsg) {
        // TODO
    }

    private fun handleFriendMessageHistoryResponse(packetMsg: IPacketMsg) {
        // TODO
    }

    private fun handleAccountInfo(packetMsg: IPacketMsg) {
        // TODO
    }

    private fun handlePersonaChangeResponse(packetMsg: IPacketMsg) {
        // TODO
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(SteamFriends::class.java)

        private fun getCallback(packetMsg: IPacketMsg): CallbackMsg? = when (packetMsg.msgType) {
            EMsg.ClientClanState -> ClanStateCallback(packetMsg)
            EMsg.ClientFriendMsgIncoming -> FriendMsgCallback(packetMsg)
            EMsg.ClientFriendMsgEchoToSender -> FriendMsgEchoCallback(packetMsg)
            EMsg.ClientAddFriendResponse -> FriendAddedCallback(packetMsg)
            EMsg.ClientChatEnter -> ChatEnterCallback(packetMsg)
            EMsg.ClientChatMsg -> ChatMsgCallback(packetMsg)
            EMsg.ClientChatMemberInfo -> ChatMemberInfoCallback(packetMsg)
            EMsg.ClientChatRoomInfo -> ChatRoomInfoCallback(packetMsg)
            EMsg.ClientChatActionResult -> ChatActionResultCallback(packetMsg)
            EMsg.ClientChatInvite -> ChatInviteCallback(packetMsg)
            EMsg.ClientSetIgnoreFriendResponse -> IgnoreFriendCallback(packetMsg)
            EMsg.ClientFriendProfileInfoResponse -> ProfileInfoCallback(packetMsg)
            else -> null
        }
    }
}
