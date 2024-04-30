package `in`.dragonbra.javasteam.steam.handlers.steamuser.callback

import com.google.protobuf.InvalidProtocolBufferException
import `in`.dragonbra.javasteam.enums.EAccountFlags
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.generated.MsgClientLogOnResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientLogonResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesParentalSteamclient
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.NetHelpers
import java.net.InetAddress
import java.util.*

/**
 * This callback is returned in response to an attempt to log on to the Steam3 network through [SteamUser].
 */
@Suppress("unused")
class LoggedOnCallback : CallbackMsg {

    /**
     * Gets the result of the logon.
     * @return the result.
     */
    val result: EResult

    /**
     * Gets the extended result of the logon.
     * @return the extended result of the logon as [EResult].
     */
    var extendedResult: EResult? = null
        private set

    /**
     * Gets the out of game secs per heartbeat value.
     *  This is used internally to initialize heartbeating.
     * @return the out of game secs per heartbeat value.
     */
    var outOfGameSecsPerHeartbeat: Int = 0
        private set

    /**
     * Gets the in game secs per heartbeat value.
     *  This is used internally to initialize heartbeating.
     * @return the in game secs per heartbeat value.
     */
    var inGameSecsPerHeartbeat: Int = 0
        private set

    /**
     * Gets or sets the public IP of the client
     * @return the public IP of the client.
     */
    var publicIP: InetAddress? = null
        private set

    /**
     * Gets the Steam3 server time.
     * @return the Steam3 server time.
     */
    var serverTime: Date? = null
        private set

    /**
     * Gets the account flags assigned by the server.
     * @return the account flags assigned by the server. See [EAccountFlags].
     */
    var accountFlags: EnumSet<EAccountFlags>? = null
        private set

    /**
     * Gets the client steam ID.
     * @return the client steam ID as [SteamID]
     */
    var clientSteamID: SteamID? = null
        private set

    /**
     * Gets the email domain.
     * @return the email domain.
     */
    var emailDomain: String? = null
        private set

    /**
     * Gets the Steam2 CellID.
     * @return the Steam2 CellID.
     */
    var cellID: Int = 0
        private set

    /**
     * Gets the Steam2 CellID ping threshold.
     * @return the Steam2 CellID ping threshold.
     */
    var cellIDPingThreshold: Int = 0
        private set

    /**
     * Gets the Steam2 ticket.
     *  This is used for authenticated content downloads in Steam2.
     *  This field will only be set when [LogOnDetails.isRequestSteam2Ticket] has been set to true.
     * @return the Steam2 ticket.
     */
    var steam2Ticket: ByteArray? = null
        private set

    /**
     * Gets the IP country code.
     * @return the IP country code.
     */
    var ipCountryCode: String? = null
        private set

    /**
     * Gets the vanity URL.
     * @return the vanity URL.
     */
    var vanityURL: String? = null
        private set

    /**
     * Gets the threshold for login failures before Steam wants the client to migrate to a new CM.
     * @return the threshold for login failures before Steam wants the client to migrate to a new CM.
     */
    var numLoginFailuresToMigrate: Int = 0
        private set

    /**
     * Gets the threshold for disconnects before Steam wants the client to migrate to a new CM.
     * @return the threshold for disconnects before Steam wants the client to migrate to a new CM.
     */
    var numDisconnectsToMigrate: Int = 0
        private set

    /**
     * Gets the Steam parental settings.
     * @return the Steam parental settings.
     */
    var parentalSettings: SteammessagesParentalSteamclient.ParentalSettings? = null
        private set

    constructor(resp: CMsgClientLogonResponse.Builder) {
        result = EResult.from(resp.eresult)
        extendedResult = EResult.from(resp.eresultExtended)

        outOfGameSecsPerHeartbeat = resp.legacyOutOfGameHeartbeatSeconds
        inGameSecsPerHeartbeat = resp.heartbeatSeconds

        publicIP = NetHelpers.getIPAddress(resp.publicIp.v4) // TODO: Has ipV6 support, but still using ipV4

        serverTime = Date(resp.rtime32ServerTime * 1000L)

        accountFlags = EAccountFlags.from(resp.accountFlags)

        clientSteamID = SteamID(resp.clientSuppliedSteamid)

        emailDomain = resp.emailDomain

        cellID = resp.cellId
        cellIDPingThreshold = resp.cellIdPingThreshold

        steam2Ticket = resp.steam2Ticket.toByteArray()

        ipCountryCode = resp.ipCountryCode

        vanityURL = resp.vanityUrl

        numLoginFailuresToMigrate = resp.countLoginfailuresToMigrate
        numDisconnectsToMigrate = resp.countDisconnectsToMigrate

        resp.parentalSettings?.let {
            try {
                parentalSettings = SteammessagesParentalSteamclient.ParentalSettings.parseFrom(it)
            } catch (e: InvalidProtocolBufferException) {
                e.printStackTrace()
            }
        }
    }

    constructor(resp: MsgClientLogOnResponse) {
        result = resp.result

        outOfGameSecsPerHeartbeat = resp.outOfGameHeartbeatRateSec
        inGameSecsPerHeartbeat = resp.inGameHeartbeatRateSec

        publicIP = NetHelpers.getIPAddress(resp.ipPublic)

        serverTime = Date(resp.serverRealTime * 1000L)

        clientSteamID = resp.clientSuppliedSteamId
    }

    constructor(eResult: EResult) {
        result = eResult
    }
}
