package `in`.dragonbra.javasteam.steam.handlers.steamuser.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg

/**
 * This callback is returned when the client is told to log off by the server.
 *
 * @param result Gets the result of the log off.
 */
class LoggedOffCallback(val result: EResult) : CallbackMsg()
