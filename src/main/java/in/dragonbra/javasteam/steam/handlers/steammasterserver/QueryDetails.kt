package `in`.dragonbra.javasteam.steam.handlers.steammasterserver

import `in`.dragonbra.javasteam.enums.ERegionCode
import java.net.InetAddress

/**
 * Details used when performing a server list query.
 */
class QueryDetails {

    /**
     * Gets or sets the AppID used when querying servers.
     * @return the AppID used when querying servers
     */
    var appID: Int = 0

    /**
     * Gets or sets the filter used for querying the master server.
     * Check [https://developer.valvesoftware.com/wiki/Master_Server_Query_Protocol](https://developer.valvesoftware.com/wiki/Master_Server_Query_Protocol) for details on how the filter is structured.
     * @return the filter used for querying the master server
     */
    var filter: String? = null

    /**
     * Gets or sets the region that servers will be returned from.
     * @return the region that servers will be returned from
     */
    var region: ERegionCode? = null

    /**
     * Gets or sets the IP address that will be GeoIP located.
     * This is done to return servers closer to this location.
     * @return the IP address that will be GeoIP located
     */
    var geoLocatedIP: InetAddress? = null

    /**
     * Gets or sets the maximum number of servers to return.
     * @return the maximum number of servers to return
     */
    var maxServers: Int = 0
}
