package `in`.dragonbra.javasteam.steam.handlers.steamuser

import com.google.protobuf.ByteString
import `in`.dragonbra.javasteam.base.ClientMsg
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.generated.MsgClientLogOnResponse
import `in`.dragonbra.javasteam.generated.MsgClientLoggedOff
import `in`.dragonbra.javasteam.generated.MsgClientLogon
import `in`.dragonbra.javasteam.generated.MsgClientMarketingMessageUpdate2
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesBase
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.AccountInfoCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.EmailAddrInfoCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.MarketingMessageCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.PlayingSessionStateCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.SessionTokenCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.VanityURLChangedCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.WalletInfoCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.WebAPIUserNonceCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.HardwareUtils
import `in`.dragonbra.javasteam.util.NetHelpers
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.util.*

/**
 * This handler handles all user log on/log off related actions and callbacks.
 */
class SteamUser : ClientMsgHandler() {

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    /**
     * Gets the [SteamID] of this client. This value is assigned after a logon attempt has succeeded.
     * @return The SteamID.
     */
    val steamID: SteamID?
        get() = client.steamID

    init {
        dispatchMap[EMsg.ClientLogOnResponse] =
            Consumer<IPacketMsg>(::handleLogOnResponse)
        dispatchMap[EMsg.ClientLoggedOff] =
            Consumer<IPacketMsg>(::handleLoggedOff)
        dispatchMap[EMsg.ClientSessionToken] =
            Consumer<IPacketMsg>(::handleSessionToken)
        dispatchMap[EMsg.ClientAccountInfo] =
            Consumer<IPacketMsg>(::handleAccountInfo)
        dispatchMap[EMsg.ClientEmailAddrInfo] =
            Consumer<IPacketMsg>(::handleEmailAddrInfo)
        dispatchMap[EMsg.ClientWalletInfoUpdate] =
            Consumer<IPacketMsg>(::handleWalletInfo)
        dispatchMap[EMsg.ClientRequestWebAPIAuthenticateUserNonceResponse] =
            Consumer<IPacketMsg>(::handleWebAPIUserNonce)
        dispatchMap[EMsg.ClientVanityURLChangedNotification] =
            Consumer<IPacketMsg>(::handleVanityURLChangedNotification)
        dispatchMap[EMsg.ClientMarketingMessageUpdate2] =
            Consumer<IPacketMsg>(::handleMarketingMessageUpdate)
        dispatchMap[EMsg.ClientPlayingSessionState] =
            Consumer<IPacketMsg>(::handlePlayingSessionState)
    }

    /**
     * Logs the client into the Steam3 network.
     * The client should already have been connected at this point.
     * Results are returned in a [LoggedOnCallback].
     * @param details The details to use for logging on.
     */
    fun logOn(details: LogOnDetails) {
        if (details.username.isNullOrEmpty() || (details.password.isNullOrEmpty() && details.accessToken.isNullOrEmpty())) {
            throw IllegalArgumentException("LogOn requires a username and password or access token to be set in 'details'.")
        }

        if (!client.isConnected) {
            LoggedOnCallback(EResult.NoConnection).also(client::postCallback)
            return
        }

        ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientLogon.Builder>(
            SteammessagesClientserverLogin.CMsgClientLogon::class.java,
            EMsg.ClientLogon
        ).apply {
            val steamID = SteamID(details.accountID, details.accountInstance, client.universe, EAccountType.Individual)
            val ipAddress: SteammessagesBase.CMsgIPAddress.Builder = SteammessagesBase.CMsgIPAddress.newBuilder()

            if (details.loginID != null) {
                // TODO: Support IPv6 login ids?
                ipAddress.setV4(details.loginID!!)

                body.setObfuscatedPrivateIp(ipAddress.build())
            } else {
                val localIp: Int = NetHelpers.getIPAddress(client.localIP)

                ipAddress.setV4(localIp xor MsgClientLogon.ObfuscationMask)
                body.setObfuscatedPrivateIp(ipAddress.build())
            }

            // Legacy field, Steam client still sets it
            if (body.obfuscatedPrivateIp.hasV4()) {
                body.setDeprecatedObfustucatedPrivateIp(body.obfuscatedPrivateIp.getV4())
            }

            protoHeader.setClientSessionid(0)
            protoHeader.setSteamid(steamID.convertToUInt64())

            body.setAccountName(details.username)
            body.setPassword(details.password)
            body.setShouldRememberPassword(details.isShouldRememberPassword)

            body.setProtocolVersion(MsgClientLogon.CurrentProtocol)
            body.setClientOsType(details.clientOSType.code())
            body.setClientLanguage(details.clientLanguage)
            body.setCellId(details.cellID ?: client.configuration.cellID)

            body.setSteam2TicketRequest(details.isRequestSteam2Ticket)

            // we're now using the latest steamclient package version, this is required to get a proper sentry file for steam guard
            body.setClientPackageVersion(1771) // todo: determine if this is still required
            body.setSupportsRateLimitResponse(true)
            body.setMachineName(details.machineName)
            body.setMachineId(ByteString.copyFrom(HardwareUtils.getMachineID()))

            // steam guard
            details.authCode?.let { body.setAuthCode(it) }
            details.twoFactorCode?.let { body.setTwoFactorCode(it) }

            details.accessToken?.let { body.setAccessToken(it) }
        }.also(client::send)
    }

    /**
     * Logs the client into the Steam3 network as an anonymous user.
     * The client should already have been connected at this point.
     * Results are returned in a [LoggedOnCallback].
     * @param details The details to use for logging on.
     */
    @JvmOverloads
    fun logOnAnonymous(details: AnonymousLogOnDetails = AnonymousLogOnDetails()) {
        if (!client.isConnected) {
            LoggedOnCallback(EResult.NoConnection).also(client::postCallback)
            return
        }

        ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientLogon.Builder>(
            SteammessagesClientserverLogin.CMsgClientLogon::class.java,
            EMsg.ClientLogon
        ).apply {
            val auId = SteamID(0, 0, client.universe, EAccountType.AnonUser)

            protoHeader.setClientSessionid(0)
            protoHeader.setSteamid(auId.convertToUInt64())

            body.setProtocolVersion(MsgClientLogon.CurrentProtocol)
            body.setClientOsType(details.clientOSType.code())
            body.setClientLanguage(details.clientLanguage)

            if (details.cellID != null) {
                body.setCellId(details.cellID!!)
            } else {
                body.setCellId(client.configuration.cellID)
            }

            body.setMachineId(ByteString.copyFrom(HardwareUtils.getMachineID()))
        }.also(client::send)
    }

    /**
     * Informs the Steam servers that this client wishes to log off from the network.
     * The Steam server will disconnect the client, and a [DisconnectedCallback] will be posted.
     */
    fun logOff() {
        isExpectDisconnection = true

        ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientLogOff.Builder>(
            SteammessagesClientserverLogin.CMsgClientLogOff::class.java,
            EMsg.ClientLogOff
        ).also(client::send)

        // TODO: 2018-02-28 it seems like the socket is not closed after getting logged of or I am doing something horribly wrong, let's disconnect here
        client.disconnect()
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleLogOnResponse(packetMsg: IPacketMsg) {
        if (packetMsg.isProto) {
            ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientLogonResponse.Builder>(
                SteammessagesClientserverLogin.CMsgClientLogonResponse::class.java,
                packetMsg
            ).also { resp ->
                LoggedOnCallback(resp.body).also(client::postCallback)
            }
        } else {
            ClientMsg(MsgClientLogOnResponse::class.java, packetMsg).also { resp ->
                LoggedOnCallback(resp.body).also(client::postCallback)
            }
        }
    }

    private fun handleLoggedOff(packetMsg: IPacketMsg) {
        val result: EResult

        if (packetMsg.isProto) {
            ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientLoggedOff.Builder>(
                SteammessagesClientserverLogin.CMsgClientLoggedOff::class.java,
                packetMsg
            ).also { resp ->
                result = EResult.from(resp.body.eresult)
            }
        } else {
            ClientMsg(MsgClientLoggedOff::class.java, packetMsg).also { resp ->
                result = resp.body.result
            }
        }

        LoggedOffCallback(result).also(client::postCallback)

        // TODO: 2018-02-28 it seems like the socket is not closed after getting logged of or I am doing something horribly wrong, let's disconnect here
        client.disconnect()
    }

    private fun handleSessionToken(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserver.CMsgClientSessionToken.Builder>(
            SteammessagesClientserver.CMsgClientSessionToken::class.java,
            packetMsg
        ).also { resp ->
            SessionTokenCallback(resp.body).also(client::postCallback)
        }
    }

    private fun handleAccountInfo(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientAccountInfo.Builder>(
            SteammessagesClientserverLogin.CMsgClientAccountInfo::class.java,
            packetMsg
        ).also { resp ->
            AccountInfoCallback(resp.body).also(client::postCallback)
        }
    }

    private fun handleEmailAddrInfo(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserver2.CMsgClientEmailAddrInfo.Builder>(
            SteammessagesClientserver2.CMsgClientEmailAddrInfo::class.java,
            packetMsg
        ).also { resp ->
            EmailAddrInfoCallback(resp.body).also(client::postCallback)
        }
    }

    private fun handleWalletInfo(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserver.CMsgClientWalletInfoUpdate.Builder>(
            SteammessagesClientserver.CMsgClientWalletInfoUpdate::class.java,
            packetMsg
        ).also { resp ->
            WalletInfoCallback(resp.body).also(client::postCallback)
        }
    }

    private fun handleWebAPIUserNonce(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientRequestWebAPIAuthenticateUserNonceResponse.Builder>(
            SteammessagesClientserverLogin.CMsgClientRequestWebAPIAuthenticateUserNonceResponse::class.java,
            packetMsg
        ).also { resp ->
            WebAPIUserNonceCallback(resp.targetJobID, resp.body).also(client::postCallback)
        }
    }

    private fun handleVanityURLChangedNotification(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserver2.CMsgClientVanityURLChangedNotification.Builder>(
            SteammessagesClientserver2.CMsgClientVanityURLChangedNotification::class.java,
            packetMsg
        ).also { resp ->
            VanityURLChangedCallback(resp.targetJobID, resp.body).also(client::postCallback)
        }
    }

    private fun handleMarketingMessageUpdate(packetMsg: IPacketMsg) {
        ClientMsg(
            MsgClientMarketingMessageUpdate2::class.java,
            packetMsg
        ).also { resp ->
            val payload: ByteArray = resp.payload.toByteArray()
            MarketingMessageCallback(resp.body, payload).also(client::postCallback)
        }
    }

    private fun handlePlayingSessionState(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserver2.CMsgClientPlayingSessionState.Builder>(
            SteammessagesClientserver2.CMsgClientPlayingSessionState::class.java,
            packetMsg
        ).also { resp ->
            PlayingSessionStateCallback(resp.targetJobID, resp.body).also(client::postCallback)
        }
    }
}
