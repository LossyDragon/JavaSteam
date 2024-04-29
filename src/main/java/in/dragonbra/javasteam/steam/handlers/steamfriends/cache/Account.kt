package `in`.dragonbra.javasteam.steam.handlers.steamfriends.cache

import `in`.dragonbra.javasteam.enums.EClanRelationship
import `in`.dragonbra.javasteam.enums.EFriendRelationship
import `in`.dragonbra.javasteam.enums.EPersonaState
import `in`.dragonbra.javasteam.enums.EPersonaStateFlag
import `in`.dragonbra.javasteam.types.GameID
import `in`.dragonbra.javasteam.types.SteamID
import java.util.EnumSet

/**
 * TODO kDoc
 *
 * @param relationship
 */
data class Clan(var relationship: EClanRelationship? = null) : Account()

/**
 * TODO kDoc
 *
 * @param relationship
 * @param personaState
 * @param personaStateFlags
 * @param gameAppID
 * @param gameID
 * @param gamaName
 */
data class User(
    var relationship: EFriendRelationship? = null,
    var personaState: EPersonaState? = null,
    var personaStateFlags: EnumSet<EPersonaStateFlag>? = null,
    var gameAppID: Int = 0,
    var gameID: GameID = GameID(),
    var gamaName: String? = null,
) : Account()

/**
 * TODO kDoc
 *
 * @param steamID
 * @param name
 * @param avatarHash
 */
abstract class Account(
    var steamID: SteamID = SteamID(),
    var name: String? = null,
    var avatarHash: ByteArray? = null,
)
