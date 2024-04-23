package `in`.dragonbra.javasteam.steam.steamclient.callbacks

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientCMList
import `in`.dragonbra.javasteam.steam.discovery.ServerRecord
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.util.NetHelpers
import java.net.InetSocketAddress
import java.util.*

/**
 * This callback is received when the client has received the CM list from Steam.
 */
class CMListCallback(cmMsg: CMsgClientCMList.Builder) : CallbackMsg() {

    /**
     * Gets the CM server list.
     * @return the CM server list.
     */
    val servers: Collection<ServerRecord>

    init {
        val addresses = cmMsg.cmAddressesList
        val ports = cmMsg.cmPortsList

        val socketServers = addresses.zip(ports).map { (address, port) ->
            val ipAddr = NetHelpers.getIPAddress(address)
            val socketAddr = InetSocketAddress(ipAddr, port)
            ServerRecord.createSocketServer(socketAddr)
        }

        val websocketServers = cmMsg.cmWebsocketAddressesList.map { address ->
            ServerRecord.createWebSocketServer(address)
        }

        servers = Collections.unmodifiableCollection(socketServers + websocketServers)
    }
}
