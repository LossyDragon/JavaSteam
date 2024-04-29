package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientSetPlayerNicknameResponse
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is fired in response to setting a nickname of a player by calling [SteamFriends.setFriendNickname].
 */
class NicknameCallback(jobID: JobID, body: CMsgClientSetPlayerNicknameResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of setting a nickname.
     * @return the result.
     */
    val result: EResult = EResult.from(body.eresult)

    init {
        this.jobID = jobID
    }
}
