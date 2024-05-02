package `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverFriends.CMsgClientPersonaState
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.PersonaState
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg

/**
 * This callback is fired in response to someone changing their friend details over the network.
 */
class PersonaStatesCallback(body: CMsgClientPersonaState.Builder) : CallbackMsg() {

    /**
     * Gets a list of friends states.
     * @return a list of friends states.
     */
    val personaStates: List<PersonaState> = body.friendsList.map { PersonaState(it) }
}
