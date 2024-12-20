package `in`.dragonbra.javasteam.steam.handlers.steamcontent

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesContentsystemSteamclient
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback.ServiceMethodResponse
import java.util.Date

/**
 * This is received when a CDN auth token is received
 */
class CDNAuthToken(message: ServiceMethodResponse<SteammessagesContentsystemSteamclient.CContentServerDirectory_GetCDNAuthToken_Response.Builder>) {

    /**
     * Result of the operation
     */
    val result: EResult = message.result

    /**
     * CDN auth token
     */
    val token: String = message.body.token

    /**
     * Token expiration date
     */
    val expiration: Date = Date(message.body.expirationTime * 1000L)
}
