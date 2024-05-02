package `in`.dragonbra.javasteam.steam.handlers.steammasterserver

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverGameservers.CMsgClientGMSServerQuery
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverGameservers.CMsgGMSClientServerQueryResponse
import `in`.dragonbra.javasteam.steam.handlers.steammasterserver.callback.QueryCallback
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import `in`.dragonbra.javasteam.util.NetHelpers
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.util.*

/**
 * This handler is used for requesting server list details from Steam.
 */
@Suppress("unused")
class SteamMasterServer : ClientMsgHandler() {

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.GMSClientServerQueryResponse] = Consumer(::handleServerQueryResponse)
    }

    /**
     * Requests a list of servers from the Steam game master server.
     * Results are returned in a [QueryCallback].
     * @param details The details for the request.
     * @return The Job ID of the request. This can be used to find the appropriate [QueryCallback].
     */
    fun serverQuery(details: QueryDetails): AsyncJobSingle<QueryCallback> {
        val query = ClientMsgProtobuf<CMsgClientGMSServerQuery.Builder>(
            CMsgClientGMSServerQuery::class.java,
            EMsg.ClientGMSServerQuery
        ).apply {
            sourceJobID = client.getNextJobID()

            body.setAppId(details.appID)

            if (details.geoLocatedIP != null) {
                body.setGeoLocationIp(NetHelpers.getIPAddress(details.geoLocatedIP))
            }

            body.setFilterText(details.filter)
            details.region?.code()?.let { body.setRegionCode(it.toInt()) }

            body.setMaxServers(details.maxServers)
        }.also(client::send)

        return AsyncJobSingle(client, query.sourceJobID)
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleServerQueryResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgGMSClientServerQueryResponse.Builder>(
            CMsgGMSClientServerQueryResponse::class.java,
            packetMsg
        ).also { queryResponse ->
            QueryCallback(queryResponse.targetJobID, queryResponse.body).also(client::postCallback)
        }
    }
}
