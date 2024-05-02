package `in`.dragonbra.javasteam.steam.handlers.steamscreenshots.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUcm.CMsgClientUCMAddScreenshotResponse
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.types.UGCHandle

/**
 * This callback is fired when a new screenshot is added.
 */
@Suppress("unused")
class ScreenshotAddedCallback(jobID: JobID, msg: CMsgClientUCMAddScreenshotResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Gets the screenshot ID of the newly added screenshot.
     * @return the screenshot.
     */
    val screenshotID: UGCHandle = UGCHandle(msg.screenshotid)

    init {
        this.jobID = jobID
    }
}
