package `in`.dragonbra.javasteam.steam.handlers.steamuserstats

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.ELeaderboardDataRequest
import `in`.dragonbra.javasteam.enums.ELeaderboardDisplayType
import `in`.dragonbra.javasteam.enums.ELeaderboardSortMethod
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgDPGetNumberOfCurrentPlayers
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgDPGetNumberOfCurrentPlayersResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLbs.CMsgClientLBSFindOrCreateLB
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLbs.CMsgClientLBSFindOrCreateLBResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLbs.CMsgClientLBSGetLBEntries
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLbs.CMsgClientLBSGetLBEntriesResponse
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.callback.FindOrCreateLeaderboardCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.callback.LeaderboardEntriesCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.callback.NumberOfPlayersCallback
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.util.*

/**
 * This handler handles Steam user statistic related actions.
 */
class SteamUserStats : ClientMsgHandler() {

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.ClientGetNumberOfCurrentPlayersDPResponse] = Consumer(::handleNumberOfPlayersResponse)
        dispatchMap[EMsg.ClientLBSFindOrCreateLBResponse] = Consumer(::handleFindOrCreateLBResponse)
        dispatchMap[EMsg.ClientLBSGetLBEntriesResponse] = Consumer(::handleGetLBEntriesResponse)
    }

    /**
     * Retrieves the number of current players for a given app id.
     *  Results are returned in a [NumberOfPlayersCallback].
     * @param appId The app id to request the number of players for.
     * @return The Job ID of the request. This can be used to find the appropriate [NumberOfPlayersCallback].
     */
    fun getNumberOfCurrentPlayers(appId: Int): AsyncJobSingle<NumberOfPlayersCallback> {
        val msg = ClientMsgProtobuf<CMsgDPGetNumberOfCurrentPlayers.Builder>(
            CMsgDPGetNumberOfCurrentPlayers::class.java,
            EMsg.ClientGetNumberOfCurrentPlayersDP
        ).apply {
            sourceJobID = client.getNextJobID()
            body.setAppid(appId)
        }.also(client::send)

        return AsyncJobSingle(client, msg.sourceJobID)
    }

    /**
     * Asks the Steam back-end for a leaderboard by name for a given appid.
     *  Results are returned in a [FindOrCreateLeaderboardCallback].
     *  The returned [AsyncJobSingle] can also be awaited to retrieve the callback result.
     * @param appId The AppID to request a leaderboard for.
     * @param name  Name of the leaderboard to request.
     * @return The Job ID of the request. This can be used to find the appropriate [FindOrCreateLeaderboardCallback].
     */
    fun findLeaderBoard(appId: Int, name: String): AsyncJobSingle<FindOrCreateLeaderboardCallback> {
        val msg = ClientMsgProtobuf<CMsgClientLBSFindOrCreateLB.Builder>(
            CMsgClientLBSFindOrCreateLB::class.java,
            EMsg.ClientLBSFindOrCreateLB
        ).apply {
            sourceJobID = client.getNextJobID()

            // routing_appid has to be set correctly to receive a response
            protoHeader.setRoutingAppid(appId)

            body.setAppId(appId)
            body.setLeaderboardName(name)
            body.setCreateIfNotFound(false)
        }.also(client::send)

        return AsyncJobSingle(client, msg.sourceJobID)
    }

    /**
     * Asks the Steam back-end for a leaderboard by name, and will create it if it's not yet.
     *  Results are returned in a [FindOrCreateLeaderboardCallback].
     *  The returned [AsyncJobSingle] can also be awaited to retrieve the callback result.
     * @param appId       The AppID to request a leaderboard for.
     * @param name        Name of the leaderboard to create.
     * @param sortMethod  Sort method to use for this leaderboard
     * @param displayType Display type for this leaderboard.
     * @return The Job ID of the request. This can be used to find the appropriate [FindOrCreateLeaderboardCallback].
     */
    fun createLeaderboard(
        appId: Int,
        name: String,
        sortMethod: ELeaderboardSortMethod,
        displayType: ELeaderboardDisplayType,
    ): AsyncJobSingle<FindOrCreateLeaderboardCallback> {
        val msg = ClientMsgProtobuf<CMsgClientLBSFindOrCreateLB.Builder>(
            CMsgClientLBSFindOrCreateLB::class.java,
            EMsg.ClientLBSFindOrCreateLB
        ).apply {
            sourceJobID = client.getNextJobID()

            // routing_appid has to be set correctly to receive a response
            protoHeader.setRoutingAppid(appId)

            body.setAppId(appId)
            body.setLeaderboardName(name)
            body.setLeaderboardDisplayType(displayType.code())
            body.setLeaderboardSortMethod(sortMethod.code())
            body.setCreateIfNotFound(true)
        }.also(client::send)

        return AsyncJobSingle(client, msg.sourceJobID)
    }

    /**
     * Asks the Steam back-end for a set of rows in the leaderboard.
     *  Results are returned in a [LeaderboardEntriesCallback].
     *  The returned [AsyncJobSingle] can also be awaited to retrieve the callback result.
     * @param appId       The AppID to request leaderboard rows for.
     * @param id          ID of the leaderboard to view.
     * @param rangeStart  Range start or 0.
     * @param rangeEnd    Range end or max leaderboard entries.
     * @param dataRequest Type of request.
     * @return The Job ID of the request. This can be used to find the appropriate [LeaderboardEntriesCallback].
     */
    fun getLeaderboardEntries(
        appId: Int,
        id: Int,
        rangeStart: Int,
        rangeEnd: Int,
        dataRequest: ELeaderboardDataRequest,
    ): AsyncJobSingle<LeaderboardEntriesCallback> {
        val msg = ClientMsgProtobuf<CMsgClientLBSGetLBEntries.Builder>(
            CMsgClientLBSGetLBEntries::class.java,
            EMsg.ClientLBSGetLBEntries
        ).apply {
            sourceJobID = client.getNextJobID()

            body.setAppId(appId)
            body.setLeaderboardId(id)
            body.setLeaderboardDataRequest(dataRequest.code())
            body.setRangeStart(rangeStart)
            body.setRangeEnd(rangeEnd)
        }.also(client::send)

        return AsyncJobSingle(client, msg.sourceJobID)
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleNumberOfPlayersResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgDPGetNumberOfCurrentPlayersResponse.Builder>(
            CMsgDPGetNumberOfCurrentPlayersResponse::class.java,
            packetMsg
        ).also { msg ->
            NumberOfPlayersCallback(msg.targetJobID, msg.body).also(client::postCallback)
        }
    }

    private fun handleFindOrCreateLBResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientLBSFindOrCreateLBResponse.Builder>(
            CMsgClientLBSFindOrCreateLBResponse::class.java,
            packetMsg
        ).also { msg ->
            FindOrCreateLeaderboardCallback(msg.targetJobID, msg.body).also(client::postCallback)
        }
    }

    private fun handleGetLBEntriesResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientLBSGetLBEntriesResponse.Builder>(
            CMsgClientLBSGetLBEntriesResponse::class.java,
            packetMsg
        ).also { msg ->
            LeaderboardEntriesCallback(msg.targetJobID, msg.body).also(client::postCallback)
        }
    }
}
