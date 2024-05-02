package `in`.dragonbra.javasteam.steam.handlers.steamscreenshots

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUcm
import `in`.dragonbra.javasteam.steam.handlers.steamscreenshots.callback.ScreenshotAddedCallback
import `in`.dragonbra.javasteam.types.AsyncJob
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.util.*

/**
 * This handler is used for screenshots.
 */
@Suppress("unused")
class SteamScreenshots : ClientMsgHandler() {

    companion object {
        /**
         * Width of a screenshot thumbnail
         */
        const val SCREENSHOT_THUMBNAIL_WIDTH: Int = 200
    }

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.ClientUCMAddScreenshotResponse] = Consumer(::handleUCMAddScreenshot)
    }

    /**
     * Adds a screenshot to the user's screenshot library. The screenshot image and thumbnail must already exist on the UFS.
     * Results are returned in a [ScreenshotAddedCallback].
     * The returned [AsyncJob] can also be awaited to retrieve the callback result.
     * @param details The details of the screenshot.
     * @return The Job ID of the request. This can be used to find the appropriate [ScreenshotAddedCallback].
     */
    fun addScreenshot(details: ScreenshotDetails): AsyncJobSingle<ScreenshotAddedCallback> {
        val msg: ClientMsgProtobuf<SteammessagesClientserverUcm.CMsgClientUCMAddScreenshot.Builder> =
            ClientMsgProtobuf<SteammessagesClientserverUcm.CMsgClientUCMAddScreenshot.Builder>(
                SteammessagesClientserverUcm.CMsgClientUCMAddScreenshot::class.java,
                EMsg.ClientUCMAddScreenshot
            ).apply {
                sourceJobID = client.getNextJobID()

                details.gameID?.let { gameID ->
                    body.setAppid(gameID.appID)
                }

                body.setCaption(details.caption)
                body.setFilename(details.ufsImageFilePath)
                body.setPermissions(details.privacy!!.code())
                body.setThumbname(details.usfThumbnailFilePath)
                body.setWidth(details.width)
                body.setHeight(details.height)
                body.setRtime32Created((details.creationTime!!.time / 1000L).toInt())
                body.setSpoilerTag(details.isContainsSpoilers)
            }.also(client::send)

        return AsyncJobSingle(client, msg.sourceJobID)
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleUCMAddScreenshot(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserverUcm.CMsgClientUCMAddScreenshotResponse.Builder>(
            SteammessagesClientserverUcm.CMsgClientUCMAddScreenshotResponse::class.java,
            packetMsg
        ).also { resp ->
            ScreenshotAddedCallback(resp.targetJobID, resp.body).also(client::postCallback)
        }
    }
}
