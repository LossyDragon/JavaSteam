package `in`.dragonbra.javasteam.steam.handlers.steamgameserver

/**
 * Represents the details required to log into Steam3 as a game server.
 */
class LogOnDetails {

    /**
     * Gets or sets the authentication token used to log in as a game server.
     * @return the authentication token used to log in as a game server
     */
    var token: String? = null

    /**
     * Gets or sets the AppID this gameserver will serve.
     * @return the AppID this gameserver will serve
     */
    var appID: Int = 0
}
