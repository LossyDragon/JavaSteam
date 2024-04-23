package `in`.dragonbra.javasteam.steam

import `in`.dragonbra.javasteam.base.ClientMsg
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IClientMsg
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.base.PacketClientMsg
import `in`.dragonbra.javasteam.base.PacketClientMsgProtobuf
import `in`.dragonbra.javasteam.base.PacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.enums.EUniverse
import `in`.dragonbra.javasteam.generated.MsgClientLogon
import `in`.dragonbra.javasteam.generated.MsgClientServerUnavailable
import `in`.dragonbra.javasteam.networking.steam3.Connection
import `in`.dragonbra.javasteam.networking.steam3.DisconnectedEventArgs
import `in`.dragonbra.javasteam.networking.steam3.EnvelopeEncryptedConnection
import `in`.dragonbra.javasteam.networking.steam3.NetMsgEventArgs
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.networking.steam3.TcpConnection
import `in`.dragonbra.javasteam.networking.steam3.UdpConnection
import `in`.dragonbra.javasteam.networking.steam3.WebSocketConnection
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesBase.CMsgMulti
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientCMList
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientSessionToken
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin
import `in`.dragonbra.javasteam.steam.discovery.ServerQuality
import `in`.dragonbra.javasteam.steam.discovery.ServerRecord
import `in`.dragonbra.javasteam.steam.discovery.SmartCMServerList
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.IDebugNetworkListener
import `in`.dragonbra.javasteam.util.MsgUtil
import `in`.dragonbra.javasteam.util.NetHelpers
import `in`.dragonbra.javasteam.util.NetHookNetworkListener
import `in`.dragonbra.javasteam.util.event.EventArgs
import `in`.dragonbra.javasteam.util.event.EventHandler
import `in`.dragonbra.javasteam.util.event.ScheduledFunction
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import `in`.dragonbra.javasteam.util.stream.BinaryReader
import `in`.dragonbra.javasteam.util.stream.MemoryStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*
import java.util.zip.GZIPInputStream

/**
 * This base client handles the underlying connection to a CM server. This class should not be use directly,
 * but through the [SteamClient] class.
 *
 * @constructor Initializes a new instance of the [CMClient] class with a specific configuration.
 * @param configuration The configuration for this client.
 * @param identifier A specific identifier to be used to uniquely identify this instance.
 * @property configuration The configuration for this client.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class CMClient(val configuration: SteamConfiguration, identifier: String) {

    /**
     * A unique identifier for this client instance.
     * @return A unique identifier for this client instance.
     */
    var id: String
        private set

    /**
     * Bootstrap list of CM servers.
     * @return Bootstrap list of CM servers.
     */
    val servers: SmartCMServerList
        get() = configuration.serverList

    /**
     * Returns the local IP of this client.
     * @return The local IP.
     */
    val localIP: InetAddress?
        get() = connection?.localIP

    /**
     * Returns the current endpoint this client is connected to.
     * @return The current endpoint.
     */
    val currentEndpoint: InetSocketAddress?
        get() = connection?.currentEndPoint

    /**
     * Gets the public IP address of this client. This value is assigned after a logon attempt has succeeded.
     * This value will be **null** if the client is logged off of Steam.
     * @return The public ip.
     */
    var publicIP: InetAddress? = null

    /**
     * Gets the country code of our public IP address according to Steam. This value is assigned after a logon attempt has succeeded.
     * This value will be **null** if the client is logged off of Steam.
     * @return The country code.
     */
    var ipCountryCode: String? = null

    /**
     * Gets the universe of this client.
     * @return The universe.
     */
    val universe: EUniverse
        get() = configuration.universe

    /**
     * Gets a value indicating whether this instance is isConnected to the remote CM server.
     * @return **true** if this instance is isConnected; otherwise, **false**.
     */
    var isConnected: Boolean = false
        private set

    /**
     * Gets the session token assigned to this client from the AM.
     * @return the session token assigned to this client from the AM.
     */
    var sessionToken: Long = 0
        private set

    /**
     * Gets the Steam recommended Cell ID of this client. This value is assigned after a logon attempt has succeeded.
     * This value will be **null** if the client is logged off of Steam.
     * @return the cell id
     */
    var cellID: Int? = null
        private set

    /**
     * Gets the session ID of this client. This value is assigned after a logon attempt has succeeded.
     * This value will be **null** if the client is logged off of Steam.
     *
     * @return The session ID.
     */
    var sessionID: Int? = null
        private set

    /**
     * Gets the SteamID of this client. This value is assigned after a logon attempt has succeeded.
     * This value will be **null** if the client is logged off of Steam.
     * @return The steam id.
     */
    var steamID: SteamID? = null
        private set

    /**
     * Gets or sets the connection timeout used when connecting to the Steam server.
     * @return The connection timeout.
     */
    val connectionTimeout: Long
        get() = configuration.connectionTimeout

    /**
     * Gets or sets the network listening interface. Use this for debugging only.
     * For your convenience, you can use [NetHookNetworkListener] class.
     */
    var debugNetworkListener: IDebugNetworkListener? = null

    /**
     *
     */
    var isExpectDisconnection: Boolean = false // TODO internal set?

    // connection lock around the setup and tear down of the connection task
    private val connectionLock = Any()

    private var connection: Connection? = null

    private lateinit var heartBeatFunc: ScheduledFunction

    private val netMsgReceived: EventHandler<NetMsgEventArgs> =
        EventHandler<NetMsgEventArgs> { _: Any, e: NetMsgEventArgs ->
            getPacketMsg(e.data).also(::onClientMsgReceived)
        }

    private val connected: EventHandler<EventArgs> =
        EventHandler<EventArgs> { _: Any, _: EventArgs ->
            servers.tryMark(connection!!.currentEndPoint!!, connection!!.protocolTypes, ServerQuality.GOOD)

            isConnected = true
            onClientConnected()
        }

    private val disconnected: EventHandler<DisconnectedEventArgs> =
        object : EventHandler<DisconnectedEventArgs> {
            override fun handleEvent(sender: Any, e: DisconnectedEventArgs) {
                isConnected = false

                if (!e.isUserInitiated && !isExpectDisconnection) {
                    servers.tryMark(connection!!.currentEndPoint!!, connection!!.protocolTypes, ServerQuality.BAD)
                }

                sessionID = null
                steamID = null

                connection!!.netMsgReceived.removeEventHandler(netMsgReceived)
                connection!!.connected.removeEventHandler(connected)
                connection!!.disconnected.removeEventHandler(this)
                connection = null

                heartBeatFunc.stop()

                onClientDisconnected(e.isUserInitiated || isExpectDisconnection)
            }
        }

    init {
        require(identifier.isNotEmpty()) { "Identifier must not be empty." }

        id = identifier

        heartBeatFunc = ScheduledFunction({
            ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientHeartBeat.Builder>(
                SteammessagesClientserverLogin.CMsgClientHeartBeat::class.java,
                EMsg.ClientHeartBeat
            ).also(::send)
        }, 5000)
    }

    /**
     * Connects this client to a Steam3 server. This begins the process of connecting and encrypting the data channel
     * between the client and the server. Results are returned asynchronously in a [ConnectedCallback]. If the
     * server that SteamKit attempts to connect to is down, a [DisconnectedCallback] will be posted instead.
     * SteamKit will not attempt to reconnect to Steam, you must handle this callback and call Connect again preferably
     * after a short delay.
     * @param cmServer The [ServerRecord] of the CM server to connect to. If **null**, JavaSteam will randomly select a CM server from its internal list.
     */
    @JvmOverloads
    fun connect(cmServer: ServerRecord? = null) {
        synchronized(connectionLock) {
            try {
                disconnect(true)

                assert(connection == null)

                isExpectDisconnection = false

                var record = cmServer
                if (record == null) {
                    record = servers.getNextServerCandidate(configuration.protocolTypes)
                }

                connection = createConnection(configuration.protocolTypes)
                connection!!.netMsgReceived.addEventHandler(netMsgReceived)
                connection!!.connected.addEventHandler(connected)
                connection!!.disconnected.addEventHandler(disconnected)
                connection!!.connect(record!!.endpoint)
            } catch (e: Exception) {
                logger.debug("Failed to connect to Steam network", e)
                onClientDisconnected(false)
            }
        }
    }

    /**
     * Disconnects this client.
     */
    @JvmOverloads
    fun disconnect(userInitiated: Boolean = true) {
        synchronized(connectionLock) {
            heartBeatFunc.stop()
            connection?.disconnect(userInitiated)
        }
    }

    /**
     * Sends the specified client message to the server. This method automatically assigns the correct SessionID and
     * SteamID of the message.
     * @param msg The client message to send.
     */
    fun send(msg: IClientMsg) {
        msg.sessionID = sessionID
        msg.steamID = steamID

        try {
            debugNetworkListener?.onOutgoingNetworkMessage(msg.msgType, msg.serialize())
        } catch (e: Exception) {
            logger.debug("DebugNetworkListener threw an exception", e)
        }

        // we'll swallow any network failures here because they will be thrown later
        // on the network thread, and that will lead to a disconnect callback
        // down the line
        connection?.send(msg.serialize())
    }

    protected open fun onClientMsgReceived(packetMsg: IPacketMsg?): Boolean {
        if (packetMsg == null) {
            logger.debug("Packet message failed to parse, shutting down connection")
            disconnect(false)
            return false
        }

        // Multi message gets logged down the line after it's decompressed
        if (packetMsg.msgType != EMsg.Multi) {
            try {
                debugNetworkListener?.onIncomingNetworkMessage(packetMsg.msgType, packetMsg.data)
            } catch (e: Exception) {
                logger.debug("debugNetworkListener threw an exception", e)
            }
        }

        when (packetMsg.msgType) {
            EMsg.Multi -> handleMulti(packetMsg)
            EMsg.ClientLogOnResponse -> handleLogOnResponse(packetMsg) // we handle this to get the SteamID/SessionID and to setup heartbeating
            EMsg.ClientLoggedOff -> handleLoggedOff(packetMsg) // to stop heartbeating when we get logged off
            EMsg.ClientServerUnavailable -> handleServerUnavailable(packetMsg)
            EMsg.ClientCMList -> handleCMList(packetMsg)
            EMsg.ClientSessionToken -> handleSessionToken(packetMsg) // am session token
            else -> Unit
        }
        return true
    }

    /**
     * Called when the client is securely isConnected to Steam3.
     */
    protected open fun onClientConnected() {
        ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientHello.Builder>(
            SteammessagesClientserverLogin.CMsgClientHello::class.java,
            EMsg.ClientHello
        ).apply {
            body.setProtocolVersion(MsgClientLogon.CurrentProtocol)
        }.also(::send)
    }

    /**
     * Called when the client is physically disconnected from Steam3.
     * @param userInitiated whether the disconnect was initialized by the client
     */
    protected open fun onClientDisconnected(userInitiated: Boolean) {
    }

    private fun createConnection(protocol: EnumSet<ProtocolTypes>): Connection {
        if (protocol.contains(ProtocolTypes.WEB_SOCKET)) {
            return WebSocketConnection()
        } else if (protocol.contains(ProtocolTypes.TCP)) {
            return EnvelopeEncryptedConnection(TcpConnection(), universe)
        } else if (protocol.contains(ProtocolTypes.UDP)) {
            return EnvelopeEncryptedConnection(UdpConnection(), universe)
        }

        throw IllegalArgumentException("Protocol bitmask has no supported protocols set.")
    }

    //region [REGION] ClientMsg Handlers
    private fun handleMulti(packetMsg: IPacketMsg) {
        if (!packetMsg.isProto) {
            logger.debug("HandleMulti got non-proto MsgMulti")
            return
        }

        val msgMulti = ClientMsgProtobuf<CMsgMulti.Builder>(CMsgMulti::class.java, packetMsg)

        var payload: ByteArray = msgMulti.body.messageBody.toByteArray()

        if (msgMulti.body.sizeUnzipped > 0) {
            try {
                val decompressedStream = MemoryStream(payload).use { compressedStream ->
                    GZIPInputStream(compressedStream).use { gzipStream ->
                        ByteArrayOutputStream().use { decompressedStream ->
                            gzipStream.copyTo(decompressedStream)
                            decompressedStream.toByteArray()
                        }
                    }
                }
                payload = decompressedStream
            } catch (e: IOException) {
                logger.debug("HandleMulti encountered an exception when decompressing.", e)
                return
            }
        }

        try {
            val ms = MemoryStream(payload)
            val br = BinaryReader(ms)

            while ((ms.length.toInt() - ms.position.toInt()) != 0) {
                val subSize = br.readInt()
                val subData = br.readBytes(subSize)

                if (!onClientMsgReceived(getPacketMsg(subData))) {
                    break
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun handleLogOnResponse(packetMsg: IPacketMsg) {
        if (!packetMsg.isProto) {
            // a non-proto ClientLogonResponse can come in as a result of connecting but never sending a ClientLogon
            // in this case, it always fails, so we don't need to do anything special here
            logger.debug("Got non-proto logon response, this is indicative of no logon attempt after connecting.")
            return
        }

        val logonResp = ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientLogonResponse.Builder>(
            SteammessagesClientserverLogin.CMsgClientLogonResponse::class.java,
            packetMsg
        )
        val logonResponse: EResult = EResult.from(logonResp.body.eresult)

        if (logonResponse == EResult.OK) {
            sessionID = logonResp.protoHeader.clientSessionid
            steamID = SteamID(logonResp.protoHeader.steamid)

            cellID = logonResp.body.cellId
            publicIP = NetHelpers.getIPAddress(logonResp.body.publicIp.v4) // TODO ipv4/6 support
            ipCountryCode = logonResp.body.ipCountryCode

            val hbDelay = logonResp.body.legacyOutOfGameHeartbeatSeconds * 1000L

            // restart heartbeat
            heartBeatFunc.stop()
            heartBeatFunc.delay = hbDelay
            heartBeatFunc.start()
        } else if (logonResponse == EResult.TryAnotherCM || logonResponse == EResult.ServiceUnavailable) {
            if (connection?.currentEndPoint != null) {
                servers.tryMark(connection!!.currentEndPoint!!, connection!!.protocolTypes, ServerQuality.BAD)
            }
        }
    }

    private fun handleLoggedOff(packetMsg: IPacketMsg) {
        sessionID = null
        steamID = null

        cellID = null
        publicIP = null
        ipCountryCode = null

        heartBeatFunc.stop()

        if (packetMsg.isProto) {
            val logoffMsg = ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientLoggedOff.Builder>(
                SteammessagesClientserverLogin.CMsgClientLoggedOff::class.java,
                packetMsg
            )
            val logoffResult = EResult.from(logoffMsg.body.eresult)

            if (logoffResult == EResult.TryAnotherCM || logoffResult == EResult.ServiceUnavailable) {
                servers.tryMark(connection!!.currentEndPoint!!, connection!!.protocolTypes, ServerQuality.BAD)
            }
        }
    }

    private fun handleServerUnavailable(packetMsg: IPacketMsg) {
        val msgClientServerUnavailable = ClientMsg(MsgClientServerUnavailable::class.java, packetMsg)
        logger.debug(
            "A server of type ${msgClientServerUnavailable.body.eServerTypeUnavailable} " +
                "was not available for request: ${EMsg.from(msgClientServerUnavailable.body.eMsgSent)}"
        )
        disconnect(false)
    }

    private fun handleCMList(packetMsg: IPacketMsg) {
        val cmMsg = ClientMsgProtobuf<CMsgClientCMList.Builder>(
            CMsgClientCMList::class.java,
            packetMsg
        )

        if (cmMsg.body.cmPortsCount != cmMsg.body.cmAddressesCount) {
            logger.debug("HandleCMList received malformed message")
        }

        val addresses: List<Int> = cmMsg.body.getCmAddressesList()
        val ports: List<Int> = cmMsg.body.getCmPortsList()

        val cmList = addresses.zip(ports).map { (address, port) ->
            val ipAddr = NetHelpers.getIPAddress(address)
            val socketAddr = InetSocketAddress(ipAddr, port)
            ServerRecord.createSocketServer(socketAddr)
        }.toMutableList()

        val sockets = cmMsg.body.getCmWebsocketAddressesList().map(ServerRecord::createWebSocketServer)
        cmList.addAll(sockets)

        // update our list with steam's list of CMs
        servers.replaceList(cmList)
    }

    private fun handleSessionToken(packetMsg: IPacketMsg) {
        val sessToken = ClientMsgProtobuf<CMsgClientSessionToken.Builder>(CMsgClientSessionToken::class.java, packetMsg)

        sessionToken = sessToken.body.token
    }
    //endregion

    companion object {
        private val logger: Logger = LogManager.getLogger(CMClient::class.java)

        @JvmStatic
        fun getPacketMsg(data: ByteArray): IPacketMsg? {
            if (data.size < 4) {
                logger.debug("PacketMsg too small to contain a message, was only {0} bytes. Message: 0x{1}")
                return null
            }

            val reader = BinaryReader(ByteArrayInputStream(data))

            var rawEMsg = 0
            try {
                rawEMsg = reader.readInt()
            } catch (e: IOException) {
                logger.debug("Exception while getting EMsg code", e)
            }

            val eMsg: EMsg = MsgUtil.getMsg(rawEMsg)

            when (eMsg) {
                EMsg.ChannelEncryptRequest,
                EMsg.ChannelEncryptResponse,
                EMsg.ChannelEncryptResult,
                -> try {
                    return PacketMsg(eMsg, data)
                } catch (e: IOException) {
                    logger.debug("Exception deserializing emsg $eMsg (${MsgUtil.isProtoBuf(rawEMsg)}).", e)
                }

                else -> Unit
            }
            try {
                return if (MsgUtil.isProtoBuf(rawEMsg)) {
                    PacketClientMsgProtobuf(eMsg, data)
                } else {
                    PacketClientMsg(eMsg, data)
                }
            } catch (e: IOException) {
                logger.debug("Exception deserializing emsg $eMsg (${MsgUtil.isProtoBuf(rawEMsg)}).", e)
                return null
            }
        }
    }
}
