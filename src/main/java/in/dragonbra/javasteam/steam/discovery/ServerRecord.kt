package `in`.dragonbra.javasteam.steam.discovery

import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.util.NetHelpers
import java.net.InetSocketAddress
import java.util.*

/**
 * Represents the information needed to connect to a CM server
 *
 * @param endpoint The endpoint of the server to connect to.
 * @param protocolTypes The various protocol types that can be used to communicate with this server.
 */
class ServerRecord private constructor(
    val endpoint: InetSocketAddress,
    val protocolTypes: EnumSet<ProtocolTypes>,
) {

    internal constructor(
        endpoint: InetSocketAddress,
        protocolTypes: ProtocolTypes,
    ) : this(endpoint, EnumSet.of(protocolTypes))

    /**
     * Gets the host of the associated endpoint.
     * @return the host of the associated endpoint.
     */
    val host: String
        get() = endpoint.hostString

    /**
     * Gets the port number of the associated endpoint.
     * @return The port number of the associated endpoint.
     */
    val port: Int
        get() = endpoint.port

    override fun equals(other: Any?): Boolean {
        if (other !is ServerRecord) {
            return false
        }

        return endpoint == other.endpoint && protocolTypes == other.protocolTypes
    }

    override fun hashCode(): Int {
        return endpoint.hashCode() xor protocolTypes.hashCode()
    }

    companion object {
        /**
         * Creates a server record for a given endpoint.
         * @param host The host to connect to.
         * @param port The port to connect to.
         * @param protocolTypes The protocol type that this server supports.
         */
        @JvmStatic
        fun createServer(host: String, port: Int, protocolTypes: ProtocolTypes): ServerRecord {
            return createServer(host, port, EnumSet.of(protocolTypes))
        }

        /**
         * Creates a server record for a given endpoint.
         * @param host The host to connect to.
         * @param port The port to connect to.
         * @param protocolTypes The protocol types that this server supports.
         */
        @JvmStatic
        fun createServer(host: String, port: Int, protocolTypes: EnumSet<ProtocolTypes>): ServerRecord {
            return ServerRecord(InetSocketAddress(host, port), protocolTypes)
        }

        /**
         * Creates a server record for a given endpoint.
         * @param endpoint The IP address and port of the server.
         */
        @JvmStatic
        fun createSocketServer(endpoint: InetSocketAddress): ServerRecord {
            return ServerRecord(endpoint, EnumSet.of(ProtocolTypes.TCP, ProtocolTypes.UDP))
        }

        /**
         * Creates a Socket server given an IP endpoint.
         * @param address The IP address and port of the server, as a string.
         * @return A new [ServerRecord], if the address was able to be parsed. **null** otherwise.
         */
        @JvmStatic
        fun tryCreateSocketServer(address: String): ServerRecord? {
            val endpoint: InetSocketAddress = NetHelpers.tryParseIPEndPoint(address) ?: return null
            return ServerRecord(endpoint, EnumSet.of(ProtocolTypes.TCP, ProtocolTypes.UDP))
        }

        /**
         * Creates a WebSocket server given an address in the form of "hostname:port".
         * @param address The name and port of the server.
         * @return A new [ServerRecord] instance.
         */
        @JvmStatic
        fun createWebSocketServer(address: String): ServerRecord {
            val defaultPort = 443
            val (hostname, port) = address.split(":").let {
                if (it.size > 1) it[0] to it[1].toInt() else address to defaultPort
            }
            val endpoint = InetSocketAddress(hostname, port)

            return ServerRecord(endpoint, ProtocolTypes.WEB_SOCKET)
        }
    }
}
