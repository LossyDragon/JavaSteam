package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.enums.EChatInfoType
import `in`.dragonbra.javasteam.generated.MsgClientChatMemberInfo
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.StateChangeDetails
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.SteamID

/**
 * This callback is fired in response to chat member info being received.
 */
@Suppress("MemberVisibilityCanBePrivate")
class ChatMemberInfoCallback(msg: MsgClientChatMemberInfo, payload: ByteArray) : CallbackMsg() {

    /**
     * Gets SteamId of the chat room.
     * @return the SteamId of the chat room.
     */
    val chatRoomID: SteamID = msg.steamIdChat

    /**
     * Gets the info type.
     * @return the info type.
     */
    val type: EChatInfoType = msg.type

    /**
     * Gets the state change info for [EChatInfoType.StateChange] member info updates.
     * @return the state change info.
     */
    var stateChangeInfo: StateChangeDetails? = null
        private set

    init {
        when (type) {
            EChatInfoType.StateChange -> stateChangeInfo = StateChangeDetails(payload)
            else -> Unit

            // todo: handle more types
            // based off disassembly
            //   - for InfoUpdate, a ChatMemberInfo object is present
            //   - for MemberLimitChange, looks like an ignored uint64 (probably steamid) followed
            //     by an int which likely represents the member limit
        }
    }
}
