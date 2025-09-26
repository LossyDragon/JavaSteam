package `in`.dragonbra.javasteam.contentdownloader

import `in`.dragonbra.javasteam.depotdownloader.Steam3Session
import `in`.dragonbra.javasteam.steam.cdn.Client
import `in`.dragonbra.javasteam.steam.cdn.Server
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.jvm.Throws

/**
 * [CDNClientPool] provides a pool of connections to CDN endpoints, requesting CDN tokens as needed
 */
class CDNClientPool(
    private val steamSession: Steam3Session,
    private val appId: Int,
    debug: Boolean = false,
) : AutoCloseable {

    private var logger: Logger? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var cdnClient: Client? = null
        private set

    var proxyServer: Server? = null
        private set

    private val servers: ArrayList<Server> = arrayListOf()

    private var nextServer: Int = 0

    init {
        cdnClient = Client(steamSession.steamClient)

        if (debug) {
            logger = LogManager.getLogger(CDNClientPool::class.java)
        }
    }

    override fun close() {
        scope.cancel()

        LogManager.removeLogger(CDNClientPool::class.java)
        logger = null
    }

    @Throws(Exception::class)
    suspend fun updateServerList(
        cellId: Int? = null,
        maxNumServers: Int? = null,
    ) {
        val serversForSteamPipe = steamSession.steamContent!!.getServersForSteamPipe(
            cellId = cellId,
            maxNumServers = maxNumServers,
            parentScope = scope
        ).await()

        proxyServer = serversForSteamPipe.firstOrNull { it.useAsProxy }

        val weightedCdnServers = serversForSteamPipe
            .filter { server ->
                val isEligibleForApp = server.allowedAppIds.isEmpty() || server.allowedAppIds.contains(appId)
                isEligibleForApp && (server.type == "SteamCache" || server.type == "CDN")
            }
            .sortedBy { it.weightedLoad }

        // ContentServerPenalty removed for now.

        servers.addAll(weightedCdnServers)

        logger?.debug(
            "Found ${servers.size} \n " + servers.joinToString(separator = "\n", prefix = "Servers:\n") { "- $it" })

        if (servers.isEmpty()) {
            throw Exception("Failed to retrieve any download servers.")
        }
    }

    fun getConnection(): Server {
        val server = servers[nextServer % servers.count()]

        logger?.debug("Getting connection $server")

        return server
    }

    fun returnConnection(server: Server?) {
        if (server == null) return

        logger?.debug("Returning connection: $server")

        // nothing to do, maybe remove from ContentServerPenalty?
    }

    fun returnBrokenConnection(server: Server?) {
        if (server == null) return

        logger?.debug("Returning broken connection: $server")

        synchronized(servers) {
            if (servers[nextServer % servers.count()] == server) {
                nextServer++

                // TODO: Add server to ContentServerPenalty
            }
        }
    }
}
