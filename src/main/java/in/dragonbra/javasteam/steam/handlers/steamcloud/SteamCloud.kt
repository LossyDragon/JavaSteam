package `in`.dragonbra.javasteam.steam.handlers.steamcloud

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUfs.CMsgClientUFSGetSingleFileInfo
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUfs.CMsgClientUFSGetSingleFileInfoResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUfs.CMsgClientUFSGetUGCDetails
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUfs.CMsgClientUFSGetUGCDetailsResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUfs.CMsgClientUFSShareFile
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUfs.CMsgClientUFSShareFileResponse
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback.ShareFileCallback
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback.SingleFileInfoCallback
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback.UGCDetailsCallback
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.types.UGCHandle
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.util.*

/**
 * This handler is used for interacting with remote storage and user generated content.
 */
class SteamCloud : ClientMsgHandler() {

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.ClientUFSGetUGCDetailsResponse] = Consumer(::handleUGCDetailsResponse)
        dispatchMap[EMsg.ClientUFSGetSingleFileInfoResponse] = Consumer(::handleSingleFileInfoResponse)
        dispatchMap[EMsg.ClientUFSShareFileResponse] = Consumer(::handleShareFileResponse)
    }

    /**
     * Requests details for a specific item of user generated content from the Steam servers.
     *  Results are returned in a [UGCDetailsCallback].
     * @param ugcId The unique user generated content id.
     * @return The Job ID of the request. This can be used to find the appropriate [UGCDetailsCallback].
     */
    fun requestUGCDetails(ugcId: UGCHandle): AsyncJobSingle<UGCDetailsCallback> {
        val request = ClientMsgProtobuf<CMsgClientUFSGetUGCDetails.Builder>(
            CMsgClientUFSGetUGCDetails::class.java,
            EMsg.ClientUFSGetUGCDetails
        ).apply {
            val jobID: JobID = client.getNextJobID()
            sourceJobID = jobID
            body.setHcontent(ugcId.value)
        }

        client.send(request)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    /**
     * Requests details for a specific file in the user's Cloud storage.
     *  Results are returned in a [SingleFileInfoCallback].
     * @param appId    The app id of the game.
     * @param filename The path to the file being requested.
     * @return The Job ID of the request. This can be used to find the appropriate [SingleFileInfoCallback].
     */
    fun getSingleFileInfo(appId: Int, filename: String?): AsyncJobSingle<SingleFileInfoCallback> {
        val request = ClientMsgProtobuf<CMsgClientUFSGetSingleFileInfo.Builder>(
            CMsgClientUFSGetSingleFileInfo::class.java,
            EMsg.ClientUFSGetSingleFileInfo
        ).apply {
            val jobID: JobID = client.getNextJobID()
            sourceJobID = jobID
            body.setAppId(appId)
            body.setFileName(filename)
        }

        client.send(request)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    /**
     * Commit a Cloud file at the given path to make its UGC handle publicly visible.
     *  Results are returned in a [ShareFileCallback].
     * @param appId    The app id of the game.
     * @param filename The path to the file being requested.
     * @return The Job ID of the request. This can be used to find the appropriate [ShareFileCallback].
     */
    fun shareFile(appId: Int, filename: String?): AsyncJobSingle<ShareFileCallback> {
        val request = ClientMsgProtobuf<CMsgClientUFSShareFile.Builder>(
            CMsgClientUFSShareFile::class.java,
            EMsg.ClientUFSShareFile
        ).apply {
            val jobID: JobID = client.getNextJobID()
            sourceJobID = jobID
            body.setAppId(appId)
            body.setFileName(filename)
        }

        client.send(request)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleUGCDetailsResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientUFSGetUGCDetailsResponse.Builder>(
            CMsgClientUFSGetUGCDetailsResponse::class.java,
            packetMsg
        ).also { infoResponse ->
            UGCDetailsCallback(infoResponse.targetJobID, infoResponse.body).also(client::postCallback)
        }
    }

    private fun handleSingleFileInfoResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientUFSGetSingleFileInfoResponse.Builder>(
            CMsgClientUFSGetSingleFileInfoResponse::class.java,
            packetMsg
        ).also { infoResponse ->
            SingleFileInfoCallback(infoResponse.targetJobID, infoResponse.body).also(client::postCallback)
        }
    }

    private fun handleShareFileResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientUFSShareFileResponse.Builder>(
            CMsgClientUFSShareFileResponse::class.java,
            packetMsg
        ).also { shareResponse ->
            ShareFileCallback(shareResponse.targetJobID, shareResponse.body).also(client::postCallback)
        }
    }
}
