package `in`.dragonbra.javasteam.steam.handlers.steamworkshop

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUcm
import `in`.dragonbra.javasteam.steam.handlers.steamworkshop.callback.UserActionPublishedFilesCallback
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.util.*

/**
 * This handler is used for requesting files published on the Steam Workshop.
 */
@Suppress("unused")
class SteamWorkshop : ClientMsgHandler() {

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.ClientUCMEnumeratePublishedFilesByUserActionResponse] =
            Consumer<IPacketMsg>(::handleEnumPublishedFilesByAction)
    }

    /**
     * Enumerates the list of published files for the current logged-in user based on user action.
     *  Results are returned in a [UserActionPublishedFilesCallback].
     *  The returned [in.dragonbra.javasteam.types.AsyncJobSingle] can also be awaited to retrieve the callback result.
     * @param details The specific details of the request.
     * @return The Job ID of the request. This can be used to find the appropriate [UserActionPublishedFilesCallback].
     */
    fun enumeratePublishedFilesByUserAction(details: EnumerationUserDetails): AsyncJobSingle<UserActionPublishedFilesCallback> {
        val enumRequest =
            ClientMsgProtobuf<SteammessagesClientserverUcm.CMsgClientUCMEnumeratePublishedFilesByUserAction.Builder>(
                SteammessagesClientserverUcm.CMsgClientUCMEnumeratePublishedFilesByUserAction::class.java,
                EMsg.ClientUCMEnumeratePublishedFilesByUserAction
            ).apply {
                sourceJobID = client.getNextJobID()

                details.userAction?.let { body.setAction(it.code()) }
                body.setAppId(details.appID)
                body.setStartIndex(details.startIndex)
            }.also(client::send)

        return AsyncJobSingle(client, enumRequest.sourceJobID)
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleEnumPublishedFilesByAction(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserverUcm.CMsgClientUCMEnumeratePublishedFilesByUserActionResponse.Builder>(
            SteammessagesClientserverUcm.CMsgClientUCMEnumeratePublishedFilesByUserActionResponse::class.java,
            packetMsg
        ).also { resp ->
            UserActionPublishedFilesCallback(resp.targetJobID, resp.body).also(client::postCallback)
        }
    }
}
