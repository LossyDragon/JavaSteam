package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.steam.cdn.Client
import `in`.dragonbra.javasteam.steam.cdn.Server
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import kotlinx.coroutines.*
import kotlin.jvm.Throws

/**
 * [CDNClientPool] provides a pool of connections to CDN endpoints, requesting CDN tokens as needed
 */
class CDNClientPool(
    private val steamSession: Steam3Session,
    private val appId: Int,
) : AutoCloseable {

    companion object {
        private val logger: Logger = LogManager.getLogger(CDNClientPool::class.java)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var cdnClient: Client? = null

    var proxyServer: Server? = null
        private set

    private val servers: ArrayList<Server> = arrayListOf()

    private var nextServer: Int = 0

    init {
        cdnClient = Client(steamSession.steamClient!!)
    }

    override fun close() {
        scope.cancel()
    }

    @Throws(Exception::class)
    suspend fun updateServerList() {
        val servers = steamSession.steamContent!!.getServersForSteamPipe(parentScope = scope).await()

        proxyServer = servers.firstOrNull { it.useAsProxy }

        val weightedCdnServers = servers
            .filter { server ->
                val isEligibleForApp = server.allowedAppIds.isEmpty() || server.allowedAppIds.contains(appId)
                isEligibleForApp && (server.type == "SteamCache" || server.type == "CDN")
            }
            .map { server ->
                val penalty = AccountSettingsStore.instance!!.contentServerPenalty[server.host] ?: 0
                server to penalty
            }
            .sortedWith(compareBy<Pair<Server, Int>> { it.second }.thenBy { it.first.weightedLoad })

        weightedCdnServers.forEach { (server, weight) ->
            repeat(server.numEntries) {
                this.servers.add(server)
            }
        }

        if (this.servers.isEmpty()) {
            throw Exception("Failed to retrieve any download servers.")
        }
    }

    fun getConnection(): Server = servers[nextServer % servers.count()]

    fun returnConnection(server: Server?) {
        if (server == null) return

        // nothing to do, maybe remove from ContentServerPenalty?
    }

    fun returnBrokenConnection(server: Server?) {
        if (server == null) return

        synchronized(servers) {
            if (servers[nextServer % servers.count()] == server) {
                nextServer++

                // TODO: Add server to ContentServerPenalty
            }
        }
    }
}
