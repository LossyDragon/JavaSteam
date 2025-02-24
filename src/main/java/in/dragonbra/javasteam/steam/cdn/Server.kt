package `in`.dragonbra.javasteam.steam.cdn

import java.net.InetSocketAddress

/**
 * Represents a single Steam3 'Steampipe' content server.
 *
 * @author Oxters
 * @author Lossy
 * @since 29-10-2024
 */
@Suppress("unused")
class Server {

    companion object {

        /**
         * Creates a Server from an InetSocketAddress
         */
        @JvmStatic
        fun fromInetSocketAddress(endpoint: InetSocketAddress): Server = Server(
            protocol = if (endpoint.port == 443) ConnectionProtocol.HTTPS else ConnectionProtocol.HTTP,
            host = endpoint.address.hostAddress,
            vHost = endpoint.address.hostAddress,
            port = endpoint.port,
        )

        /**
         * Creates a Server from a hostname and port
         */
        @JvmStatic
        fun fromHostAndPort(hostname: String, port: Int): Server = Server(
            protocol = if (port == 443) ConnectionProtocol.HTTPS else ConnectionProtocol.HTTP,
            host = hostname,
            vHost = hostname,
            port = port,
        )
    }

    /**
     * The protocol used to connect to this server
     */
    enum class ConnectionProtocol {
        /**
         * Server does not advertise HTTPS support, connect over HTTP
         */
        HTTP,

        /**
         * Server advertises it supports HTTPS, connection made over HTTPS
         */
        HTTPS,
    }

    constructor()

    constructor(protocol: ConnectionProtocol, host: String, vHost: String, port: Int) {
        this.protocol = protocol
        this.host = host
        this.vHost = vHost
        this.port = port
    }

    /**
     * Gets the supported connection protocol of the server.
     */
    var protocol: ConnectionProtocol = ConnectionProtocol.HTTP
        internal set

    /**
     * Gets the hostname of the server.
     */
    var host: String? = null
        internal set

    /**
     * Gets the virtual hostname of the server.
     */
    var vHost: String? = null
        internal set

    /**
     * Gets the port of the server.
     */
    var port: Int = 0
        internal set

    /**
     * Gets the type of the server.
     */
    var type: String? = null
        internal set

    /**
     * Gets the SourceID this server belongs to.
     */
    var sourceID: Int = 0
        internal set

    /**
     * Gets the CellID this server belongs to.
     */
    var cellID: Int = 0
        internal set

    /**
     * Gets the load value associated with this server.
     */
    var load: Int = 0
        internal set

    /**
     * Gets the weighted load.
     */
    var weightedLoad: Float = 0f
        internal set

    /**
     * Gets the number of entries this server is worth.
     */
    var numEntries: Int = 0
        internal set

    /**
     * Gets the flag whether this server is for Steam China only.
     */
    var steamChinaOnly: Boolean = false
        internal set

    /**
     * Gets the download proxy status.
     */
    var useAsProxy: Boolean = false
        internal set

    /**
     * Gets the transformation template applied to request paths.
     */
    var proxyRequestPathTemplate: String? = null
        internal set

    /**
     * Gets the list of app ids this server can be used with.
     */
    var allowedAppIds: IntArray = IntArray(0)
        internal set

    /**
     * Returns a [String] that represents this server.
     * @return a [String] that represents this server.
     */
    override fun toString(): String = "$host:$port ($type)"
}
