package `in`.dragonbra.javasteam.steam.handlers.steamuserstats.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLbs.CMsgClientLBSGetLBEntriesResponse
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.LeaderboardEntry
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is fired in response to [SteamUserStats.getLeaderboardEntries].
 */
@Suppress("unused")
class LeaderboardEntriesCallback(jobID: JobID, resp: CMsgClientLBSGetLBEntriesResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of the request.
     * @return the result.
     */
    val result: EResult = EResult.from(resp.eresult)

    /**
     * How many entires there are for requested leaderboard.
     * @return how many entries there are for requested leaderboard.
     */
    val entryCount: Int = resp.leaderboardEntryCount

    /**
     * Gets the list of leaderboard entries this response contains.
     * @return the list of leaderboard entries this response contains. See [LeaderboardEntry]
     */
    val entries: List<LeaderboardEntry> = resp.entriesList.map { LeaderboardEntry(it) }

    init {
        this.jobID = jobID
    }
}
