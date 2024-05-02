package `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUfs.CMsgClientUFSShareFileResponse
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is received in response to calling [SteamCloud.shareFile].
 */
@Suppress("unused")
class ShareFileCallback(jobID: JobID, msg: CMsgClientUFSShareFileResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of the request.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Gets the resulting UGC handle.
     * @return the resulting UGC handle.
     */
    val ugcId: Long = msg.hcontent

    init {
        this.jobID = jobID
    }
}
