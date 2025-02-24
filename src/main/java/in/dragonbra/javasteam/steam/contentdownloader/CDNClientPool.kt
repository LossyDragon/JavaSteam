package `in`.dragonbra.javasteam.steam.contentdownloader

import `in`.dragonbra.javasteam.steam.cdn.Client
import `in`.dragonbra.javasteam.steam.cdn.Server
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [CDNClientPool] provides a pool of connections to CDN endpoints, requesting CDN tokens as needed
 */
class CDNClientPool(
    internal val steamClient: SteamClient,
    private val appId: Int,
    private val parentScope: CoroutineScope,
) {

    companion object {
        private const val SERVER_ENDPOINT_MIN_SIZE = 8
    }

    val cdnClient: Client = Client(steamClient)

    var proxyServer: Server? = null
        private set

    private val activeConnectionPool = ConcurrentLinkedDeque<Server>()

    private val availableServerEndpoints = ConcurrentLinkedQueue<Server>()

    private val populatePoolEvent = CountDownLatch(1)

    private val monitorJob: Job

    private val logger: Logger = LogManager.getLogger(CDNClientPool::class.java)

    init {
        monitorJob = parentScope.launch { connectionPoolMonitor().await() }
    }

    fun shutdown() {
        monitorJob.cancel()
    }

    private fun fetchBootstrapServerList(): Deferred<List<Server>?> = parentScope.async {
        return@async try {
            val cdnServers = steamClient.getHandler(SteamContent::class.java)
                ?.getServersForSteamPipe(parentScope = parentScope)?.await()

            logger.debug("Fetched bootstrap servers: ${cdnServers?.size}")

            if (cdnServers != null) {
                return@async cdnServers
            } else {
                return@async null
            }
        } catch (ex: Exception) {
            logger.error("Failed to retrieve content server list", ex)

            null
        }
    }

    private fun connectionPoolMonitor() = parentScope.async {
        var didPopulate = false

        while (isActive) {
            populatePoolEvent.await(1, TimeUnit.SECONDS)

            if (availableServerEndpoints.size < SERVER_ENDPOINT_MIN_SIZE && steamClient.isConnected) {
                val servers = fetchBootstrapServerList().await()

                if (servers.isNullOrEmpty()) {
                    logger.error("Servers is empty or null, exiting connection pool monitor")
                    parentScope.cancel()
                    return@async
                }

                proxyServer = servers.find { it.useAsProxy }

                val weightedCdnServers = servers
                    .filter { server ->
                        val isEligibleForApp = server.allowedAppIds.isEmpty() || appId in server.allowedAppIds
                        isEligibleForApp && (server.type == "SteamCache" || server.type == "CDN")
                    }
                    .sortedBy { it.weightedLoad }

                for (server in weightedCdnServers) {
                    repeat(server.numEntries) {
                        availableServerEndpoints.offer(server)
                    }
                }

                didPopulate = true
            } else if (availableServerEndpoints.isEmpty() && !steamClient.isConnected && didPopulate) {
                logger.error("Available server endpoints is empty and steam is not connected, exiting connection pool monitor")

                parentScope.cancel()

                return@async
            }
        }
    }

    private fun buildConnection(): Deferred<Server?> = parentScope.async {
        return@async try {
            if (availableServerEndpoints.size < SERVER_ENDPOINT_MIN_SIZE) {
                populatePoolEvent.countDown()
            }

            var output: Server? = null

            while (isActive && availableServerEndpoints.poll().also { output = it } == null) {
                delay(1000)
            }

            output
        } catch (e: Exception) {
            logger.error("Failed to build connection", e)

            null
        }
    }

    fun getConnection(): Deferred<Server?> = parentScope.async {
        return@async try {
            val connection = activeConnectionPool.poll() ?: buildConnection().await()

            logger.debug("Get Connection: $connection")

            connection
        } catch (e: Exception) {
            logger.error("Failed to get/build connection", e)

            null
        }
    }

    internal fun returnConnection(server: Server?) {
        server?.let { server ->
            activeConnectionPool.push(server)
        }
    }

    @Suppress("unused")
    internal fun returnBrokenConnection(server: Server?) {
        server?.let { server ->
            // Broken connections are not returned to the pool
        }
    }
}
