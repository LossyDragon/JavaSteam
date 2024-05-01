package `in`.dragonbra.javasteam.steam.webapi

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.discovery.ServerRecord
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import java.io.IOException

/**
 * Helper class to load servers from the Steam Directory Web API.
 */
object SteamDirectory {

    /**
     * Load a list of servers from the Steam Directory.
     * @param configuration Configuration Object
     * @param maxServers Max number of servers to return. The API will typically return this number per server type
     * (socket and websocket). If negative, the parameter is not added to the request
     * @return the list of servers
     * @throws IOException if the request could not be executed
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun load(configuration: SteamConfiguration, maxServers: Int = -1): List<ServerRecord> {
        val api: WebAPI = configuration.getWebAPI("ISteamDirectory")

        val params: MutableMap<String, String> = HashMap()

        params["cellid"] = configuration.cellID.toString()

        if (maxServers >= 0) {
            params["maxcount"] = maxServers.toString()
        }

        val response = api.call(function = "GetCMList", parameters = params)

        val result: EResult = EResult.from(response["result"].asInteger(EResult.Invalid.code()))

        check(result == EResult.OK) {
            "Steam Web API returned EResult.$result"
        }

        val socketList = response["serverlist"]
        val webSocketList = response["serverlist_websockets"]

        val records = socketList.children.mapNotNull { ServerRecord.tryCreateSocketServer(it.value) }
        val webSocket = webSocketList.children.mapNotNull { ServerRecord.createWebSocketServer(it.value) }

        return records + webSocket
    }
}
