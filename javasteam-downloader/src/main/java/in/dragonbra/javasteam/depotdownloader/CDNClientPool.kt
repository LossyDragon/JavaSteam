package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.steam.cdn.Client
import `in`.dragonbra.javasteam.steam.cdn.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CDNClientPool provides a pool of connections to CDN endpoints, requesting CDN tokens as needed
 */
class CDNClientPool(
    private val steamSession: Steam3Session,
    private val appId: Int,
) {
    val cdnClient: Client = Client(steamSession.steamClient)

    var proxyServer: Server? = null
        private set

    private val servers: MutableList<Server> = mutableListOf()
    private var nextServer: Int = 0

    suspend fun updateServerList() = withContext(Dispatchers.IO) {
        val servers = steamSession.steamContent.getServersForSteamPipe(parentScope = this).await()

        proxyServer = servers.firstOrNull { it.useAsProxy }

        val weightedCdnServers = servers
            .filter { server ->
                val isEligibleForApp = server.allowedAppIds.isEmpty() || appId in server.allowedAppIds
                isEligibleForApp && (server.type == "SteamCache" || server.type == "CDN")
            }.map { server ->
                val penalty = AccountSettingsStore.instance!!.contentServerPenalty[server.host] ?: 0
                server to penalty
            }.sortedWith(compareBy<Pair<Server, Int>> { it.second }.thenBy { it.first.weightedLoad })

        @Suppress("UNUSED_VARIABLE")
        for ((server, weight) in weightedCdnServers) {
            repeat(server.numEntries) {
                this@CDNClientPool.servers.add(server)
            }
        }

        if (this@CDNClientPool.servers.isEmpty()) {
            throw Exception("Failed to retrieve any download servers.")
        }
    }

    fun getConnection(): Server = servers[nextServer % servers.size]

    fun returnConnection(server: Server?) {
        if (server == null) return

        // nothing to do, maybe remove from ContentServerPenalty?
    }

    fun returnBrokenConnection(server: Server?) {
        if (server == null) return

        synchronized(servers) {
            if (servers[nextServer % servers.size] == server) {
                nextServer++

                // TODO: Add server to ContentServerPenalty
            }
        }
    }
}
