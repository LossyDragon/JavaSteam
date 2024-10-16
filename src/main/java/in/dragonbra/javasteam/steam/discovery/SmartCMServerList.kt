package `in`.dragonbra.javasteam.steam.discovery

import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.steam.webapi.SteamDirectory
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.EnumSet

/**
 * Smart list of CM servers.
 */
@Suppress("unused")
class SmartCMServerList(private val configuration: SteamConfiguration) {

    companion object {
        private val logger: Logger = LogManager.getLogger(SmartCMServerList::class.java)

        /**
         * The default fallback Websockets server to attempt connecting to if fetching server list through other means fails.
         * If the default server set here no longer works, please create a pull request to update it or file an issue.
         * [Issue Tracker](https://github.com/Longi94/JavaSteam/issues)
         */
        @JvmStatic
        var defaultServerWebSocket = "cmp1-ord1.steamserver.net"

        /**
         * The default fallback TCP/UDP server to attempt connecting to if fetching server list through other means fails.
         * If the default server set here no longer works, please create a pull request to update it or file an issue.
         * [Issue Tracker](https://github.com/Longi94/JavaSteam/issues)
         */
        @JvmStatic
        var defaultServerNetFilter = "ext1-ord1.steamserver.net"
    }

    private val servers: MutableList<ServerInfo> = mutableListOf()
    private var serversLastRefresh: Instant = Instant.MIN

    /**
     * Determines how long a server's bad connection state is remembered for.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var badConnectionMemoryTimeSpan: Duration = Duration.ofMinutes(5)

    /**
     * Determines how long the server list cache is used as-is before attempting to refresh from the Steam Directory.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var serverListBeforeRefreshTimeSpan: Duration = Duration.ofDays(7)

    @Throws(IOException::class)
    private fun startFetchingServers() {
        if (servers.isNotEmpty()) {
            // if the server list has been populated, no need to perform any additional work
            if (Duration.between(serversLastRefresh, Instant.now()) >= serverListBeforeRefreshTimeSpan) {
                resolveServerList(forceRefresh = true)
            } else {
                // no work needs to be done
            }
        } else {
            resolveServerList()
        }
    }

    @Throws(IOException::class)
    private fun resolveServerList(forceRefresh: Boolean = false) {
        var forcedRefresh = forceRefresh

        val providerRefreshTime = configuration.serverListProvider.lastServerListRefresh
        var alreadyTriedDirectoryFetch = false

        // If this is the first time the server list is being resolved,
        // check if the cache is old enough that requires refreshing from the API first
        if (!forceRefresh && Duration.between(providerRefreshTime, Instant.now()) >= serverListBeforeRefreshTimeSpan) {
            forcedRefresh = true
        }

        // Server list can only be force refreshed if the API is allowed in the first place
        if (forcedRefresh && configuration.isAllowDirectoryFetch) {
            logger.debug("Querying `SteamDirectory` for a fresh server list")

            val directoryList = SteamDirectory.load(configuration)

            alreadyTriedDirectoryFetch = true

            // Fresh server list has been loaded
            if (directoryList.isNotEmpty()) {
                logger.debug("Resolved ${directoryList.size} servers from `SteamDirectory`")

                replaceList(directoryList, writeProvider = true, Instant.now())
                return
            }

            logger.debug("Could not query `SteamDirectory`, falling back to provider")
        } else {
            logger.debug("Resolving server list using the provider")
        }

        val serverList = configuration.serverListProvider.fetchServerList()
        var endpointList = serverList.toList()

        // Provider server list is fresh enough and it provided servers
        if (endpointList.isNotEmpty()) {
            logger.debug("Resolved ${endpointList.size} servers from the provider")
            replaceList(endpointList, writeProvider = false, providerRefreshTime)
            return
        }

        // If API fetch is not allowed, bail out with no servers
        if (!configuration.isAllowDirectoryFetch) {
            logger.debug("Server list provider had no entries, and `SteamConfiguration.isAllowDirectoryFetch` is false")
            replaceList(listOf(), writeProvider = false, Instant.MIN)
            return
        }

        // If the force refresh tried to fetch the server list already, do not fetch it again
        if (!alreadyTriedDirectoryFetch) {
            logger.debug("Server list provider had no entries, will query `SteamDirectory`")
            endpointList = SteamDirectory.load(configuration)

            if (endpointList.isNotEmpty()) {
                logger.debug("Resolved ${endpointList.size} servers from `SteamDirectory`")
                replaceList(endpointList, writeProvider = true, Instant.now())
                return
            }
        }

        // This is a last effort to attempt any valid connection to Steam
        logger.debug("Server list provider had no entries, `SteamDirectory` failed, falling back to default server $defaultServerNetFilter")

        val resolved = InetAddress.getAllByName(defaultServerNetFilter).filterIsInstance<Inet4Address>()

        if (resolved.isEmpty()) {
            logger.debug("Failed to resolve default server $defaultServerNetFilter to any address")
            replaceList(listOf(), writeProvider = false, Instant.now())
            return
        }

        endpointList = resolved.map { ipAddr ->
            ServerRecord.createSocketServer(InetSocketAddress(ipAddr, 27017))
        }.plus(ServerRecord.createWebSocketServer(defaultServerWebSocket)).toList()

        replaceList(endpointList, writeProvider = false, Instant.MIN)
    }

    /**
     * Resets the scores of all servers which has a last bad connection more than [SmartCMServerList.badConnectionMemoryTimeSpan] ago.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun resetOldScores() {
        val cutoff = Instant.now().minus(badConnectionMemoryTimeSpan)

        servers.forEach { serverInfo ->
            serverInfo.lastBadConnectionTimeUtc?.let { lastConnectionTime ->
                if (lastConnectionTime.isBefore(cutoff)) {
                    serverInfo.lastBadConnectionTimeUtc = null
                }
            }
        }
    }

    /**
     * Replace the list with a new list of servers provided to us by the Steam servers.
     *
     * @param endpointList The [ServerRecord] to use for this [SmartCMServerList].
     * @param writeProvider If true, the replaced list will be updated in the server list provider.
     * @param serversTime The time when the provided server list has been updated.
     */
    @JvmOverloads
    fun replaceList(endpointList: List<ServerRecord>, writeProvider: Boolean = true, serversTime: Instant? = null) {
        val distinctEndPoints = endpointList.distinct()

        serversLastRefresh = serversTime ?: Instant.now()
        servers.clear()

        distinctEndPoints.forEach(::addCore)

        if (writeProvider) {
            configuration.serverListProvider.updateServerList(distinctEndPoints)
        }
    }

    private fun addCore(endPoint: ServerRecord) {
        endPoint.protocolTypes.forEach { protocolType ->
            val info = ServerInfo(endPoint, protocolType)
            servers.add(info)
        }
    }

    /**
     * Explicitly resets the known state of all servers.
     */
    fun resetBadServers() {
        servers.forEach { serverInfo ->
            serverInfo.lastBadConnectionTimeUtc = null
        }
    }

    fun tryMark(endPoint: InetSocketAddress, protocolTypes: ProtocolTypes, quality: ServerQuality): Boolean =
        tryMark(endPoint, EnumSet.of(protocolTypes), quality)

    fun tryMark(endPoint: InetSocketAddress, protocolTypes: EnumSet<ProtocolTypes>, quality: ServerQuality): Boolean {
        val serverInfos: List<ServerInfo>

        if (quality == ServerQuality.GOOD) {
            serverInfos = servers.filter { x ->
                x.record.endpoint == endPoint && protocolTypes.contains(x.protocol)
            }
        } else {
            // If we're marking this server for any failure, mark all endpoints for the host at the same time
            val host = endPoint.hostString
            serverInfos = servers.filter { x ->
                x.record.host == host
            }
        }

        if (serverInfos.isEmpty()) {
            return false
        }

        for (serverInfo in serverInfos) {
            logger.debug("Marking ${serverInfo.record.endpoint} - ${serverInfo.protocol} as $quality")
            markServerCore(serverInfo, quality)
        }

        return true
    }

    private fun markServerCore(serverInfo: ServerInfo, quality: ServerQuality) {
        when (quality) {
            ServerQuality.GOOD -> serverInfo.lastBadConnectionTimeUtc = null
            ServerQuality.BAD -> serverInfo.lastBadConnectionTimeUtc = Instant.now()
        }
    }

    /**
     * Perform the actual score lookup of the server list and return the candidate.
     *
     * @param supportedProtocolTypes The minimum supported [ProtocolTypes] of the server to return.
     * @return An [ServerRecord], or null if the list is empty.
     */
    private fun getNextServerCandidateInternal(supportedProtocolTypes: EnumSet<ProtocolTypes>): ServerRecord? {
        resetOldScores()

        val result = servers
            .asSequence()
            .filter { supportedProtocolTypes.contains(it.protocol) }
            .mapIndexed { index, server -> server to index }
            .sortedWith(compareBy({ it.first.lastBadConnectionTimeUtc ?: Instant.EPOCH }, { it.second }))
            .map { it.first }
            .firstOrNull()

        if (result == null) {
            return null
        }

        logger.debug("Next server candidate: ${result.record.endpoint} (${result.protocol})")
        return ServerRecord(result.record.endpoint, result.protocol)
    }

    /**
     * Get the next server in the list.
     *
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
     *
     * @param supportedProtocolTypes The minimum supported [ProtocolTypes] of the server to return.
     * @return An [ServerRecord], or null if the list is empty.
     */
    fun getNextServerCandidate(supportedProtocolTypes: ProtocolTypes): ServerRecord? =
        getNextServerCandidate(EnumSet.of(supportedProtocolTypes))

    /**
     * Gets the [ServerRecords][ServerRecord] of all servers in the server list.
     *
     * @return An [List] array contains the [ServerRecords][ServerRecord] of the servers in the list
     */
    fun getAllEndPoints(): List<ServerRecord?> {
        runCatching {
            startFetchingServers()
        }.onFailure { return emptyList() }

        val endPoints = servers.map { s -> s.record }.distinct()

        return endPoints
    }

    // TODO maybe return a completion result
    /**
     * Force refresh the server list. If directory fetch is allowed, it will refresh from the API first,
     * and then fallback to the server list provider.
     **/
    fun forceRefreshServerList() {
        resolveServerList(true)
    }
}
