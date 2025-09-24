package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.util.Versions
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.UserAgent
import kotlinx.coroutines.isActive

object HttpClientFactory {
    val httpClient by lazy {
        HttpClient(CIO) {
            install(UserAgent) {
                agent = "DepotDownloader/${Versions.getVersion()}"
            }
            engine {
                maxConnectionsCount = 10
                endpoint {
                    maxConnectionsPerRoute = 5
                    pipelineMaxSize = 20
                    keepAliveTime = 5000
                    connectTimeout = 5000
                    requestTimeout = 30000
                }
            }
        }
    }

    fun close() {
        if (httpClient.isActive) {
            httpClient.close()
        }
    }
}
