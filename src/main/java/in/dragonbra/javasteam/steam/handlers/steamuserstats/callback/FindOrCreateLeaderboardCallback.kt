package `in`.dragonbra.javasteam.steam.handlers.steamuserstats.callback

import `in`.dragonbra.javasteam.enums.ELeaderboardDisplayType
import `in`.dragonbra.javasteam.enums.ELeaderboardSortMethod
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLbs.CMsgClientLBSFindOrCreateLBResponse
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is fired in response to [SteamUserStats.findLeaderBoard] and [SteamUserStats.createLeaderboard].
 */
@Suppress("unused")
class FindOrCreateLeaderboardCallback(jobID: JobID, resp: CMsgClientLBSFindOrCreateLBResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of the request.
     * @return the result of the request by [EResult].
     */
    val result: EResult = EResult.from(resp.eresult)

    /**
     * Gets the leaderboard ID.
     * @return the leaderboard ID.
     */
    val id: Int = resp.leaderboardId

    /**
     * Gets how many entries there are for requested leaderboard.
     * @return how many entries there are for requested leaderboard.
     */
    val entryCount: Int = resp.leaderboardEntryCount

    /**
     * Gets the sort method to use for this leaderboard.
     * @return sort method to use for this leaderboard. See [ELeaderboardSortMethod].
     */
    val sortMethod: ELeaderboardSortMethod = ELeaderboardSortMethod.from(resp.leaderboardSortMethod)

    /**
     * Gets the display type for this leaderboard.
     * @return display type for this leaderboard. See [ELeaderboardDisplayType]
     */
    val displayType: ELeaderboardDisplayType = ELeaderboardDisplayType.from(resp.leaderboardDisplayType)

    init {
        this.jobID = jobID
    }
}
