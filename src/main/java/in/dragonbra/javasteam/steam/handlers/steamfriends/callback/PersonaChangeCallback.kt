package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgPersonaChangeResponse
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * This callback is fired in response to setting this client's persona name or state
 *  with [SteamFriends.setPersonaName] or [SteamFriends.setPersonaState].
 */
class PersonaChangeCallback(jobID: JobID, msg: CMsgPersonaChangeResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of changing this client's persona information.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.result)

    /**
     * Gets the name of this client according to Steam.
     * @return the name of this client according to Steam.
     */
    val name: String = msg.playerName

    init {
        this.jobID = jobID
    }
}
