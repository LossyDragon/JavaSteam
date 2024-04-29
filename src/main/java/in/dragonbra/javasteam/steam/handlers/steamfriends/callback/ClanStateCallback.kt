package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.enums.EAccountFlags
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientClanState
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.Event
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.SteamID
import java.util.*

/**
 * This callback is posted when a clan's state has been changed.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ClanStateCallback(msg: CMsgClientClanState.Builder) : CallbackMsg() {

    /**
     * Gets the [SteamID] of the clan that posted this state update.
     * @return the [SteamID] of the clan that posted this state update.
     */
    val clanID: SteamID = SteamID(msg.steamidClan)

    /**
     * Gets the account flags.
     * @return the account flags.
     */
    val accountFlags: EnumSet<EAccountFlags> = EAccountFlags.from(msg.clanAccountFlags)

    /**
     * Gets the privacy of the chat room.
     * @return the privacy of the chat room.
     */
    val isChatRoomPrivate: Boolean = msg.chatRoomPrivate

    /**
     * Gets the name of the clan.
     * @return the name of the clan.
     */
    var clanName: String? = null
        private set

    /**
     * Gets the SHA-1 avatar hash.
     * @return the SHA-1 avatar hash.
     */
    var avatarHash: ByteArray? = null
        private set

    /**
     * Gets the total number of members in this clan.
     * @return the total number of members in this clan.
     */
    var memberTotalCount: Int = 0
        private set

    /**
     * Gets the number of members in this clan that are currently online.
     * @return the number of members in this clan that are currently online.
     */
    var memberOnlineCount: Int = 0
        private set

    /**
     * Gets the number of members in this clan that are currently chatting.
     * @return the number of members in this clan that are currently chatting.
     */
    var memberChattingCount: Int = 0
        private set

    /**
     * Gets the number of members in this clan that are currently in-game.
     * @return the number of members in this clan that are currently in-game.
     */
    var memberInGameCount: Int = 0
        private set

    /**
     * Gets any events associated with this clan state update.
     * @return any events associated with this clan state update. See [Event]
     */
    var events: List<Event> = msg.eventsList.map { Event(it) }
        private set

    /**
     * Gets any announcements associated with this clan state update.
     * @return any announcements associated with this clan state update. See [Event]
     */
    var announcements: List<Event> = msg.announcementsList.map { Event(it) }
        private set

    init {
        if (msg.hasNameInfo()) {
            clanName = msg.nameInfo.clanName
            avatarHash = msg.nameInfo.shaAvatar.toByteArray()
        }

        if (msg.hasUserCounts()) {
            memberTotalCount = msg.userCounts.members
            memberOnlineCount = msg.userCounts.online
            memberChattingCount = msg.userCounts.chatting
            memberInGameCount = msg.userCounts.inGame
        }
    }
}
