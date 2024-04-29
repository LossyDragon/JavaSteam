package `in`.dragonbra.javasteam.steam.handlers.steamfriends

import `in`.dragonbra.javasteam.types.SteamID
import java.util.*

/**
 * Represents a single Message sent to or received from a friend
 *
 * @constructor TODO kDoc constructor
 * @param steamID the [SteamID] of the User that wrote the message.
 * @param isUnread whether the message has been read, i.e., is an offline message.
 * @param message the actual message.
 * @param timestamp the time (in UTC) when the message was sent.
 */
data class FriendMessage(
    val steamID: SteamID,
    val isUnread: Boolean,
    val message: String,
    val timestamp: Date,
)
