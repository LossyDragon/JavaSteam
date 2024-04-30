package `in`.dragonbra.javasteam.steam.handlers.steamuser.callback

import `in`.dragonbra.javasteam.enums.EMsg.ClientGamesPlayed
import `in`.dragonbra.javasteam.enums.EResult.LoggedInElsewhere
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientPlayingSessionState
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is received when another client starts or stops playing a game.
 *  While [isPlayingBlocked], sending [ClientGamesPlayed]
 *  message will log you off with [LoggedInElsewhere] result.
 */
class PlayingSessionStateCallback(jobID: JobID, msg: CMsgClientPlayingSessionState.Builder) : CallbackMsg() {

    /**
     * Indicates whether playing is currently blocked by another client.
     * @return **true** if blocked by another client, otherwise **false**.
     */
    val isPlayingBlocked: Boolean = msg.playingBlocked

    /**
     * When blocked, gets the appid which is currently being played.
     * @return the app id.
     */
    val playingAppID: Int = msg.playingApp

    init {
        this.jobID = jobID
    }
}
