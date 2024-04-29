package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.generated.MsgClientSetIgnoreFriendResponse
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is fired in response to an attempt at ignoring a friend.
 */
class IgnoreFriendCallback(jobID: JobID, response: MsgClientSetIgnoreFriendResponse) : CallbackMsg() {

    /**
     * Gets the result of ignoring a friend.
     * @return the result.
     */
    val result: EResult = response.result

    init {
        this.jobID = jobID
    }
}
