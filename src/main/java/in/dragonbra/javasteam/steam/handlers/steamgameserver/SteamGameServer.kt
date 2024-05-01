package `in`.dragonbra.javasteam.steam.handlers.steamgameserver

import com.google.protobuf.ByteString
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.enums.EServerFlags
import `in`.dragonbra.javasteam.generated.MsgClientLogon
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverGameservers
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin
import `in`.dragonbra.javasteam.steam.handlers.steamgameserver.callback.StatusReplyCallback
import `in`.dragonbra.javasteam.steam.handlers.steamgameserver.callback.TicketAuthCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.HardwareUtils
import `in`.dragonbra.javasteam.util.NetHelpers
import `in`.dragonbra.javasteam.util.Utils
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.net.Inet6Address
import java.util.*

/**
 * This handler is used for interacting with the Steam network as a game server.
 */
@Suppress("unused")
class SteamGameServer : ClientMsgHandler() {

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.GSStatusReply] = Consumer<IPacketMsg>(::handleStatusReply)
        dispatchMap[EMsg.ClientTicketAuthComplete] = Consumer<IPacketMsg>(::handleAuthComplete)
    }

    /**
     * Logs onto the Steam network as a persistent game server.
     * The client should already have been connected at this point.
     * Results are return in a [LoggedOnCallback].
     * @param details The details to use for logging on.
     */
    fun logOn(details: LogOnDetails) {
        require(!details.token.isNullOrEmpty()) { "LogOn requires a game server token to be set in 'details'." }

        if (!client.isConnected) {
            LoggedOnCallback(EResult.NoConnection).also(client::postCallback)
            return
        }

        ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientLogon.Builder>(
            SteammessagesClientserverLogin.CMsgClientLogon::class.java,
            EMsg.ClientLogonGameServer
        ).apply {
            val gsId = SteamID(0, 0, client.universe, EAccountType.GameServer)

            protoHeader.setClientSessionid(0)
            protoHeader.setSteamid(gsId.convertToUInt64())

            val localIp: Int = NetHelpers.getIPAddress(client.localIP)
            body.setDeprecatedObfustucatedPrivateIp(localIp xor MsgClientLogon.ObfuscationMask) // NOTE: Using deprecated method.
            // body.setObfuscatedPrivateIp() // TODO: Look into impl this instead of the above.

            body.setProtocolVersion(MsgClientLogon.CurrentProtocol)

            body.setClientOsType(Utils.OSType.code())
            body.setGameServerAppId(details.appID)
            body.setMachineId(ByteString.copyFrom(HardwareUtils.machineID))

            body.setGameServerToken(details.token)
        }.also(client::send)
    }

    /**
     * Logs the client into the Steam3 network as an anonymous game server.
     * The client should already have been connected at this point.
     * Results are return in a [LoggedOnCallback].
     * @param appId The AppID served by this game server, or 0 for the default.
     */
    @JvmOverloads
    fun logOnAnonymous(appId: Int = 0) {
        if (!client.isConnected) {
            LoggedOnCallback(EResult.NoConnection).also(client::postCallback)
            return
        }

        ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientLogon.Builder>(
            SteammessagesClientserverLogin.CMsgClientLogon::class.java,
            EMsg.ClientLogonGameServer
        ).apply {
            val gsId = SteamID(0, 0, client.universe, EAccountType.AnonGameServer)
            protoHeader.setClientSessionid(0)
            protoHeader.setSteamid(gsId.convertToUInt64())

            val localIp: Int = NetHelpers.getIPAddress(client.localIP)
            body.setDeprecatedObfustucatedPrivateIp(localIp xor MsgClientLogon.ObfuscationMask) // NOTE: Using deprecated method.
            // body.setObfuscatedPrivateIp() // TODO: Look into impl this instead of the above.

            body.setProtocolVersion(MsgClientLogon.CurrentProtocol)

            body.setClientOsType(Utils.OSType.code())
            body.setGameServerAppId(appId)
            body.setMachineId(ByteString.copyFrom(HardwareUtils.machineID))
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

    /**
     * Sends the server's status to the Steam network.
     * Results are returned in a [StatusReplyCallback] callback.
     * @param details A [StatusDetails] object containing the server's status.
     */
    fun sendStatus(details: StatusDetails) {
        require(!(details.address != null && details.address is Inet6Address)) { "Only IPv4 addresses are supported." }

        ClientMsgProtobuf<SteammessagesClientserverGameservers.CMsgGSServerType.Builder>(
            SteammessagesClientserverGameservers.CMsgGSServerType::class.java,
            EMsg.GSServerType
        ).apply {
            body.setAppIdServed(details.appID)
            body.setFlags(EServerFlags.code(details.serverFlags))
            body.setGameDir(details.gameDirectory)
            body.setGamePort(details.port)
            body.setGameQueryPort(details.queryPort)
            body.setGameVersion(details.version)
            if (details.address != null) {
                body.setDeprecatedGameIpAddress(NetHelpers.getIPAddress(details.address)) // NOTE: Using deprecated method.
            }
        }.also(client::send)
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleStatusReply(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserverGameservers.CMsgGSStatusReply.Builder>(
            SteammessagesClientserverGameservers.CMsgGSStatusReply::class.java,
            packetMsg
        ).also { statusReply ->
            StatusReplyCallback(statusReply.body).also(client::postCallback)
        }
    }

    private fun handleAuthComplete(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserver.CMsgClientTicketAuthComplete.Builder>(
            SteammessagesClientserver.CMsgClientTicketAuthComplete::class.java,
            packetMsg
        ).also { statusReply ->
            TicketAuthCallback(statusReply.body).also(client::postCallback)
        }
    }
}
