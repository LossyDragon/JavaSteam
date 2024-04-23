package `in`.dragonbra.javasteam.steam.discovery

import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.steam.webapi.SteamDirectory
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Smart list of CM servers.
 *
 * @constructor Initialize SmartCMServerList with a given server list provider.
 * @param configuration The Steam configuration to use.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class SmartCMServerList(private val configuration: SteamConfiguration) {

    private val servers: MutableList<ServerInfo> = Collections.synchronizedList(ArrayList())

    /**
     * Determines how long a server's bad connection state is remembered for.
     * @return a [Duration] to remember a bad connection state.
     */
    var badConnectionMemoryTimeSpan: Duration = 5.minutes

    /**
     * Gets the [ServerRecord] of all servers in the server list.
     * @return An [List] array contains the [ServerRecord] of the servers in the list
     */
    val allEndPoints: List<ServerRecord>
        get() = try {
            startFetchingServers()
            servers.map { it.record }.distinct()
        } catch (e: IOException) {
            emptyList()
        }

    @Throws(IOException::class)
    private fun startFetchingServers() {
        // if the server list has been populated, no need to perform any additional work
        if (servers.isNotEmpty()) {
            return
        }

        resolveServerList()
    }

    @Throws(IOException::class)
    private fun resolveServerList() {
        logger.debug("Resolving server list")

        var endPoints = configuration.serverListProvider.fetchServerList()

        if (endPoints.isEmpty() && configuration.isAllowDirectoryFetch) {
            logger.debug("Server list provider had no entries, will query SteamDirectory")
            endPoints = SteamDirectory.load(configuration)
        }

        // Could not query steam directory, fallback to a random one from
        // https://api.steampowered.com/ISteamDirectory/GetCMList/v1/?cellid=0
        if (endPoints.isEmpty() && configuration.isAllowDirectoryFetch) {
            val ip = InetAddress.getByName("162.254.198.46") // Chosen April 23, 2024 by Azerai

            logger.debug("Could not query SteamDirectory, falling back to a random CM ${ip.hostAddress}")

            val fallback = InetSocketAddress(ip, 27018)
            endPoints = listOf(ServerRecord.createSocketServer(fallback))
        }

        logger.debug("Resolved ${endPoints.size} servers")
        replaceList(endPoints)
    }

    /**
     * Resets the scores of all servers which has a last bad connection more than [badConnectionMemoryTimeSpan] ago.
     */
    fun resetOldScores() {
        val cutoff = System.currentTimeMillis() - badConnectionMemoryTimeSpan.inWholeMilliseconds

        servers.forEach { serverInfo ->
            serverInfo.lastBadConnection?.let {
                if (it.time < cutoff) {
                    serverInfo.lastBadConnection = null
                }
            }
        }
    }

    /**
     * Replace the list with a new list of servers provided to us by the Steam servers.
     * @param endPoints The [ServerRecord] to use for this [SmartCMServerList].
     */
    fun replaceList(endPoints: List<ServerRecord>) {
        servers.clear()

        endPoints.distinct().forEach(::addCore)

        configuration.serverListProvider.updateServerList(endPoints)
    }

    private fun addCore(endPoint: ServerRecord) {
        endPoint.protocolTypes.forEach {
            servers.add(ServerInfo(endPoint, it))
        }
    }

    /**
     * Explicitly resets the known state of all servers.
     */
    fun resetBadServers() {
        servers.forEach { serverInfo ->
            serverInfo.lastBadConnection = null
        }
    }

    fun tryMark(endPoint: InetSocketAddress, protocolTypes: ProtocolTypes, quality: ServerQuality): Boolean {
        return tryMark(endPoint, EnumSet.of(protocolTypes), quality)
    }

    fun tryMark(endPoint: InetSocketAddress, protocolTypes: EnumSet<ProtocolTypes>, quality: ServerQuality): Boolean {
        val serverInfos = servers.filter {
            it.record.endpoint == endPoint && protocolTypes.contains(it.protocol)
        }.onEach {
            logger.debug("Marking ${it.record.endpoint} - ${it.protocol} as $quality")
            markServerCore(it, quality)
        }

        return serverInfos.isNotEmpty()
    }

    private fun markServerCore(serverInfo: ServerInfo, quality: ServerQuality) {
        when (quality) {
            ServerQuality.GOOD -> serverInfo.lastBadConnection = null
            ServerQuality.BAD -> serverInfo.lastBadConnection = Date()
        }
    }

    /**
     * Perform the actual score lookup of the server list and return the candidate.
     * @param supportedProtocolTypes The minimum supported [ProtocolTypes] of the server to return.
     * @return An [ServerRecord], or null if the list is empty.
     */
    private fun getNextServerCandidateInternal(supportedProtocolTypes: EnumSet<ProtocolTypes>): ServerRecord? {
        resetOldScores()

        val serverInfos = servers.filter { supportedProtocolTypes.contains(it.protocol) }
        val sortedServerInfos = serverInfos.sortedWith(
            compareBy<ServerInfo> {
                it.lastBadConnection != null
            }.thenBy {
                it.lastBadConnection
            }
        )

        return sortedServerInfos.firstOrNull()?.let {
            ServerRecord(it.record.endpoint, it.protocol)
        }
    }

    /**
     * Get the next server in the list.
     * @param supportedProtocolTypes The minimum supported [ProtocolTypes] of the server to return.
     * @return An [ServerRecord], or null if the list is empty.
     */
    fun getNextServerCandidate(supportedProtocolTypes: EnumSet<ProtocolTypes>): ServerRecord? {
        try {
            startFetchingServers()
        } catch (e: IOException) {
            return null
        }

        return getNextServerCandidateInternal(supportedProtocolTypes)
    }

    /**
     * Get the next server in the list.
     * @param supportedProtocolTypes The minimum supported [ProtocolTypes] of the server to return.
     * @return An [ServerRecord], or null if the list is empty.
     */
    fun getNextServerCandidate(supportedProtocolTypes: ProtocolTypes): ServerRecord? {
        return getNextServerCandidate(EnumSet.of(supportedProtocolTypes))
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(SmartCMServerList::class.java)
    }
}
