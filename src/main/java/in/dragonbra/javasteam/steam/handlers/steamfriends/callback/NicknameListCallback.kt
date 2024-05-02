package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientPlayerNicknameList
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.PlayerNickname
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg

/**
 * This callback is fired when the client receives a list of friend nicknames.
 */
class NicknameListCallback(msg: CMsgClientPlayerNicknameList.Builder) : CallbackMsg() {

    /**
     * Gets the name of nicknames.
     * @return the list of nicknames.
     */
    val nicknames: List<PlayerNickname> = msg.nicknamesList.map { PlayerNickname(it) }
}
