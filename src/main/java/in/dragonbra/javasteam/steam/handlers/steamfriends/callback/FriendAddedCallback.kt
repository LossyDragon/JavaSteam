package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientAddFriendResponse
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.SteamID

/**
 * This callback is fired in response to adding a user to your friends list.
 */
class FriendAddedCallback(msg: CMsgClientAddFriendResponse.Builder) : CallbackMsg() {

    /**
     * Gets the result of the request.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Gets the [SteamID] of the friend that was added.
     * @return the [SteamID] of the friend that was added.
     */
    val steamID: SteamID = SteamID(msg.steamIdAdded)

    /**
     * Gets the persona name of the friend.
     * @return the persona name of the friend.
     */
    val personaName: String = msg.personaNameAdded
}
