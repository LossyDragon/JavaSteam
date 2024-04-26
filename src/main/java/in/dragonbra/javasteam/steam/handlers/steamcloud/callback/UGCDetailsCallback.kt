package `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUfs.CMsgClientUFSGetUGCDetailsResponse
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.types.SteamID

/**
 * This callback is received in response to calling [SteamCloud.requestUGCDetails].
 */
class UGCDetailsCallback(jobID: JobID, msg: CMsgClientUFSGetUGCDetailsResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of the request.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Gets the App ID the UGC is for.
     * @return the App ID.
     */
    val appID: Int = msg.appId

    /**
     * Gets the SteamID of the UGC's creator.
     * @return the SteamID.
     */
    val creator: SteamID = SteamID(msg.steamidCreator)

    /**
     * Gets the URL that the content is located at.
     * @return the URL that the content is located at.
     */
    val url: String = msg.url

    /**
     * Gets the name of the file.
     * @return the name of the file.
     */
    val fileName: String = msg.filename

    /**
     * Gets the size of the file.
     * @return the size of the file.
     */
    val fileSize: Int = msg.fileSize

    init {
        this.jobID = jobID
    }
}
