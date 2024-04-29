package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientFriendProfileInfoResponse
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.types.SteamID
import java.util.*

/**
 * This callback is fired in response to requesting profile info for a user.
 */
@Suppress("unused")
class ProfileInfoCallback : CallbackMsg {

    /**
     * Gets the result of requesting profile info.
     * @return the result.
     */
    val result: EResult

    /**
     * Gets the [SteamID] this info belongs to.
     * @return the [SteamID] this info belongs to.
     */
    val steamID: SteamID

    /**
     * Gets the time this account was created.
     * @return the time this account was created.
     */
    val timeCreated: Date

    /**
     * Gets the real name.
     * @return the real name.
     */
    val realName: String

    /**
     * Gets the name of the city.
     * @return the name of the city.
     */
    val cityName: String

    /**
     * Gets the name of the state.
     * @return the name of the state.
     */
    val stateName: String

    /**
     * Gets the name of the country.
     * @return the name of the country.
     */
    val countryName: String

    /**
     * Gets the headline.
     * @return the headline.
     */
    val headline: String

    /**
     * Gets the summary.
     * @return the summary.
     */
    val summary: String

    /**
     * Primary constructor for handler
     */
    constructor(jobID: JobID, response: CMsgClientFriendProfileInfoResponse.Builder) {
        this.jobID = jobID
        result = EResult.from(response.eresult)
        steamID = SteamID(response.steamidFriend)
        timeCreated = Date(response.timeCreated * 1000L)
        realName = response.realName
        cityName = response.cityName
        stateName = response.stateName
        countryName = response.countryName
        headline = response.headline
        summary = response.summary
    }

    /**
     * TODO kDoc
     */
    constructor(
        result: EResult,
        steamID: SteamID,
        timeCreated: Date,
        realName: String,
        cityName: String,
        stateName: String,
        countryName: String,
        headline: String,
        summary: String,
    ) {
        this.result = result
        this.steamID = steamID
        this.timeCreated = timeCreated
        this.realName = realName
        this.cityName = cityName
        this.stateName = stateName
        this.countryName = countryName
        this.headline = headline
        this.summary = summary
    }
}
