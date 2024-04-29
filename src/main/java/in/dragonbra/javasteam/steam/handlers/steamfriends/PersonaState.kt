package `in`.dragonbra.javasteam.steam.handlers.steamfriends

import `in`.dragonbra.javasteam.enums.EClientPersonaStateFlag
import `in`.dragonbra.javasteam.enums.EPersonaState
import `in`.dragonbra.javasteam.enums.EPersonaStateFlag
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientPersonaState
import `in`.dragonbra.javasteam.types.GameID
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.NetHelpers
import java.net.InetAddress
import java.util.*

/**
 * Represents the persona state of a friend.
 */
@Suppress("unused")
class PersonaState(friend: CMsgClientPersonaState.Friend) {

    /**
     * Gets the status flags. This shows what has changed.
     * @return The status flags.
     */
    val statusFlags: EnumSet<EClientPersonaStateFlag> = EClientPersonaStateFlag.from(friend.personaStateFlags)

    /**
     * Gets the friend ID.
     * @return the friend [SteamID]
     */
    val friendID: SteamID = SteamID(friend.friendid)

    /**
     * Gets the state.
     * @return the state.
     */
    val state: EPersonaState = EPersonaState.from(friend.personaState)

    /**
     * Gets the state flags.
     * @return the state flags.
     */
    val stateFlags: EnumSet<EPersonaStateFlag> = EPersonaStateFlag.from(friend.personaStateFlags)

    /**
     * Gets the game app ID.
     * @return the game app ID.
     */
    val gameAppID: Int = friend.gamePlayedAppId

    /**
     * Gets the game ID.
     * @return the game ID.
     */
    val gameID: GameID = GameID(friend.gameid)

    /**
     *  Gets the name of the game.
     * @return the name of the game.
     */
    val gameName: String = friend.gameName

    /**
     * Gets the game server IP.
     * @return the game server IP.
     */
    val gameServerIP: InetAddress = NetHelpers.getIPAddress(friend.gameServerIp)

    /**
     * Gets the game server port.
     * @return the game server port.
     */
    val gameServerPort: Int = friend.gameServerPort

    /**
     * Gets the query port.
     * @return the query port.
     */
    val queryPort: Int = friend.queryPort

    /**
     * Gets the source steam ID.
     * @return the source [SteamID].
     */
    val sourceSteamID: SteamID = SteamID(friend.steamidSource)

    /**
     * Gets the game data blob.
     * @return the game data blob.
     */
    val gameDataBlob: ByteArray = friend.gameDataBlob.toByteArray()

    /**
     * Gets the name.
     * @return the name.
     */
    val name: String = friend.playerName

    /**
     * Gets the avatar hash.
     * @return the avatar hash.
     */
    val avatarHash: ByteArray = friend.avatarHash.toByteArray()

    /**
     * Gets the last log off.
     * @return the last log off.
     */
    val lastLogOff: Date = Date(friend.lastLogoff * 1000L)

    /**
     * Gets the last log on.
     * @return the last log on.
     */
    val lastLogOn: Date = Date(friend.lastLogon * 1000L)

    /**
     * Gets the clan rank.
     * @return the clan rank.
     */
    val clanRank: Int = friend.clanRank

    /**
     * Gets the clan tag.
     * @return the clan tag.
     */
    val clanTag: String = friend.clanTag

    /**
     * Gets the online session instances.
     * @return the online session instance.
     */
    val onlineSessionInstances: Int = friend.onlineSessionInstances
}
