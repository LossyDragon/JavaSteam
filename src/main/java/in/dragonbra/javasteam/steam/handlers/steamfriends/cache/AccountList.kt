package `in`.dragonbra.javasteam.steam.handlers.steamfriends.cache

import `in`.dragonbra.javasteam.types.SteamID
import java.util.concurrent.*

// TODO Junit test this
/**
 * TODO kDoc
 */
class AccountList<T : Account> : ConcurrentHashMap<SteamID, T>() {
    fun getAccount(steamId: SteamID, cls: Class<out T>): T = getOrPut(steamId) {
        cls.getConstructor().newInstance().apply { steamID = steamId }
    }
}
