package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.steam.cdn.Client
import `in`.dragonbra.javasteam.steam.cdn.Server
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
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
    private val steamClient: SteamClient,
    private val appId: Int,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    debug: Boolean = false,
) : AutoCloseable {

    companion object {
        fun init(
            steamClient: SteamClient,
            appId: Int,
            debug: Boolean,
        ): CDNClientPool = CDNClientPool(steamClient = steamClient, appId = appId, debug = debug)
    }

    private var logger: Logger? = null

    var cdnClient: Client? = null
        private set

    var proxyServer: Server? = null
        private set

    private val servers: ArrayList<Server> = arrayListOf()

    private var nextServer: Int = 0

    init {
        cdnClient = Client(steamClient)

        if (debug) {
            logger = LogManager.getLogger(CDNClientPool::class.java)
        }
    }

    override fun close() {
        scope.cancel()

        servers.clear()

        cdnClient = null
        proxyServer = null

        LogManager.removeLogger(CDNClientPool::class.java)
        logger = null
    }

    @Throws(Exception::class)
    suspend fun updateServerList(maxNumServers: Int? = null) {
        if (servers.isNotEmpty()) {
            servers.clear()
        }

        val serversForSteamPipe = steamClient.getHandler<SteamContent>()!!.getServersForSteamPipe(
            cellId = steamClient.cellID ?: 0,
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

        //  servers.joinToString(separator = "\n", prefix = "Servers:\n") { "- $it" }
        logger?.debug("Found ${servers.size} Servers: \n")

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
