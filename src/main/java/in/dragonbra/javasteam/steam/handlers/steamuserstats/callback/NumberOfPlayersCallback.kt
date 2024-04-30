package `in`.dragonbra.javasteam.steam.handlers.steamuserstats.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgDPGetNumberOfCurrentPlayersResponse
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is fired in response to [SteamUserStats.getNumberOfCurrentPlayers].
 */
@Suppress("unused")
class NumberOfPlayersCallback(jobID: JobID, resp: CMsgDPGetNumberOfCurrentPlayersResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of the request.
     * @return the result of the request by [EResult].
     */
    val result: EResult = EResult.from(resp.eresult)

    /**
     * Gets the current number of players according to Steam.
     * @return the current number of players according to Steam.
     */
    val numPlayers: Int = resp.playerCount

    init {
        this.jobID = jobID
    }
}
