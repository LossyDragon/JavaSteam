package `in`.dragonbra.javasteam.steam.handlers.steamgameserver

import `in`.dragonbra.javasteam.enums.EServerFlags
import java.net.InetAddress
import java.util.*

/**
 * Represents the details of the game server's current status.
 */
class StatusDetails {

    /**
     * Gets or sets the AppID this game server is serving.
     * @return the AppID this game server is serving
     */
    var appID: Int = 0

    /**
     * Gets or sets the server's basic state as flags.
     * @return the server's basic state as flags
     */
    var serverFlags: EnumSet<EServerFlags>? = null

    /**
     * Gets or sets the directory the game data is in.
     * @return the directory the game data is in
     */
    var gameDirectory: String? = null

    /**
     * Gets or sets the IP address the game server listens on.
     * @return the IP address the game server listens on
     */
    var address: InetAddress? = null

    /**
     * Gets or sets the port the game server listens on.
     * @return the port the game server listens on
     */
    var port: Int = 0

    /**
     * Gets or sets the port the game server responds to queries on.
     * @return the port the game server responds to queries on
     */
    var queryPort: Int = 0

    /**
     * Gets or sets the current version of the game server.
     * @return the current version of the game server
     */
    var version: String? = null
}
