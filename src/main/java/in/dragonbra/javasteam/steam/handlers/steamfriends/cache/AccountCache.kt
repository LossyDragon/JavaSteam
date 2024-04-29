package `in`.dragonbra.javasteam.steam.handlers.steamfriends.cache

import `in`.dragonbra.javasteam.types.SteamID

/**
 * TODO kDoc
 */
@Suppress("MemberVisibilityCanBePrivate")
class AccountCache {

    /**
     *
     */
    val localUser: User = User()

    /**
     *
     */
    val users: AccountList<User> = AccountList()

    /**
     *
     */
    val clans: AccountList<Clan> = AccountList()

    /**
     *
     */
    fun getUser(steamID: SteamID): User = if (isLocalUser(steamID)) {
        localUser
    } else {
        users.getAccount(steamID, User::class.java)
    }

    /**
     *
     */
    fun isLocalUser(steamID: SteamID): Boolean = localUser.steamID == steamID
}
