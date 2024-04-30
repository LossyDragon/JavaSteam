package `in`.dragonbra.javasteam.steam.handlers.steamworkshop.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUcm.CMsgClientUCMEnumeratePublishedFilesByUserActionResponse
import `in`.dragonbra.javasteam.steam.handlers.steamworkshop.SteamWorkshop
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID
import java.util.*

/**
 * This callback is received in response to calling [SteamWorkshop.enumeratePublishedFilesByUserAction].
 */
class UserActionPublishedFilesCallback(
    jobID: JobID,
    msg: CMsgClientUCMEnumeratePublishedFilesByUserActionResponse.Builder,
) : CallbackMsg() {

    /**
     * Gets the result.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Gets the list of enumerated files.
     * @return the list of enumerated files.
     */
    val files: List<File> = msg.publishedFilesList.map { File(it) }

    /**
     * Gets the count of total results.
     * @return the count of total results.
     */
    val totalResults: Int = msg.totalResults

    init {
        this.jobID = jobID
    }

    /**
     * Represents the details of a single published file.
     */
    @Suppress("unused")
    class File(file: CMsgClientUCMEnumeratePublishedFilesByUserActionResponse.PublishedFileId) {

        /**
         * Gets the file ID.
         * @return the file ID.
         */
        val fileID: Long = file.publishedFileId

        /**
         * Gets the timestamp of this file.
         * @return the timestamp of this file.
         */
        val timestamp: Date = Date(file.rtimeTimeStamp * 1000L)
    }
}
