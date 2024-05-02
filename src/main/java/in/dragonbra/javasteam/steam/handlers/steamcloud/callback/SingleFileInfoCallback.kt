package `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUfs.CMsgClientUFSGetSingleFileInfoResponse
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID
import java.util.*

/**
 * This callback is received in response to calling [SteamCloud.getSingleFileInfo].
 */
@Suppress("unused")
class SingleFileInfoCallback(jobID: JobID, msg: CMsgClientUFSGetSingleFileInfoResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of the request.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Gets the App ID the file is for.
     * @return the App ID the file is for.
     */
    val appID: Int = msg.appId

    /**
     * Gets the file name request.
     * @return the file name request.
     */
    val fileName: String = msg.fileName

    /**
     * Gets the SHA hash of the file.
     * @return the SHA hash of the file.
     */
    val shaHash: ByteArray = msg.shaFile.toByteArray()

    /**
     * Gets the timestamp of the file.
     * @return the timestamp of the file.
     */
    val timestamp: Date = Date(msg.timeStamp * 1000L)

    /**
     * Gets the size of the file.
     * @return the size of the file.
     */
    val fileSize: Int = msg.rawFileSize

    /**
     * Gets if the file was explicity deleted by the user.
     * @return if the file was explicitly deleted by the user.
     */
    val isExplicitDelete: Boolean = msg.isExplicitDelete

    init {
        this.jobID = jobID
    }
}
