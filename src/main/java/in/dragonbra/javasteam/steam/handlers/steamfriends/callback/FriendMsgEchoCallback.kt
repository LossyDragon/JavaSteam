package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.enums.EChatEntryType
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientFriendMsgIncoming
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.SteamID
import java.nio.charset.StandardCharsets

/**
 * This callback is fired in response to receiving an echo message from another instance.
 */
@Suppress("unused")
class FriendMsgEchoCallback(msg: CMsgClientFriendMsgIncoming.Builder) : CallbackMsg() {

    /**
     * Gets or sets the recipient
     * @return The recipient.
     */
    val recipient: SteamID = SteamID(msg.steamidFrom)

    /**
     * Gets the chat entry type.
     * @return the chat entry type.
     */
    val entryType: EChatEntryType = EChatEntryType.from(msg.chatEntryType)

    /**
     * Gets a value indicating whether this message is from a limited account.
     * @return **true** if this message is from a limited account; otherwise, **false**.
     */
    val isFromLimitedAccount: Boolean = msg.fromLimitedAccount

    /**
     * Gets the message.
     * @return the message.
     */
    var message: String? = null
        private set

    /**
     * Gets the timestamp from the server
     * @return the timestamp from the server.
     */
    val rTime32ServerTimestamp: Int = msg.rtime32ServerTimestamp

    init {
        if (msg.hasMessage()) {
            message = msg.message.toString(StandardCharsets.UTF_8)
            message = message!!.replace("\u0000+$".toRegex(), "") // trim any extra null chars from the end
        }
    }
}
