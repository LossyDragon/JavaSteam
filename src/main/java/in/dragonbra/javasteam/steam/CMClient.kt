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
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientSessionToken
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientHeartBeat
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientLoggedOff
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientLogonResponse
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
import `in`.dragonbra.javasteam.util.Strings
import `in`.dragonbra.javasteam.util.event.EventArgs
import `in`.dragonbra.javasteam.util.event.EventHandler
import `in`.dragonbra.javasteam.util.event.ScheduledFunction
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import `in`.dragonbra.javasteam.util.stream.BinaryReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream

/**
 * This base client handles the underlying connection to a CM server. This class should not be use directly,
 * but through the [SteamClient] class.
 *
 * @constructor Initializes a new instance of the [CMClient] class with a specific configuration.
 * @param configuration The configuration to use for this client.
 * @param identifier A specific identifier to be used to uniquely identify this instance.
 * @property configuration The configuration for this client.
 * @property identifier A unique identifier for this client instance.
 */
abstract class CMClient(val configuration: SteamConfiguration, val identifier: String) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Bootstrap list of CM servers.
     */
    val servers: SmartCMServerList
        get() = configuration.serverList

    /**
     * Returns the local IP of this client.
     */
    val localIP: InetAddress
        get() = connection.get()!!.localIP

    /**
     * Returns the current endpoint this client is connected to.
     */
    val currentEndpoint: InetSocketAddress
        get() = connection.get()!!.currentEndPoint

    /**
     * Gets the public IP address of this client. This value is assigned after a logon attempt has succeeded.
     * This value will be **null** if the client is logged off of Steam.
     */
    var publicIP: InetAddress? = null
        private set

    /**
     * Gets the country code of our public IP address according to Steam. This value is assigned after a logon attempt has succeeded.
     * This value will be **null** if the client is logged off of Steam.
     */
    var ipCountryCode: String? = null
        private set

    /**
     * Gets the universe of this client.
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
     */
    var sessionToken: Long = 0
        private set

    /**
     * Gets the Steam recommended Cell ID of this client. This value is assigned after a logon attempt has succeeded.
     * This value will be **null** if the client is logged off of Steam.
     */
    var cellID: Int? = null
        private set

    /**
     * Gets the session ID of this client. This value is assigned after a logon attempt has succeeded.
     * This value will be **null** if the client is logged off of Steam.
     */
    var sessionID: Int? = null
        private set

    /**
     * Gets the SteamID of this client. This value is assigned after a logon attempt has succeeded.
     * This value will be **null** if the client is logged off of Steam.
     */
    var steamID: SteamID? = null
        private set

    /**
     * Gets or sets the connection timeout used when connecting to the Steam server.
     */
    val connectionTimeout: Long
        get() = configuration.connectionTimeout

    /**
     * Gets or sets the network listening interface. Use this for debugging only.
     * For your convenience, you can use [NetHookNetworkListener] class.
     */
    private var debugNetworkListener: IDebugNetworkListener? = null

    @get:JvmName("getExpectDisconnection")
    @set:JvmName("setExpectDisconnection")
    internal var isExpectDisconnection: Boolean = false

    // connection lock around the setup and tear down of the connection task
    private val connectionLock = Any()

    private var connection: AtomicReference<Connection?> = AtomicReference(null)

    private val heartBeatFunc: ScheduledFunction

    private val netMsgReceived = EventHandler<NetMsgEventArgs> { _: Any?, e: NetMsgEventArgs ->
        onClientMsgReceived(getPacketMsg(e.data))
    }

    private val connected = EventHandler<EventArgs> { sender: Any?, e: EventArgs? ->
        logger.debug("EventHandler `connected` called")
        servers.tryMark(connection.get()!!.currentEndPoint, connection.get()!!.protocolTypes, ServerQuality.GOOD)

        isConnected = true

        try {
            onClientConnected()
        } catch (ex: Exception) {
            logger.error("Unhandled exception after connecting: ", ex)
            disconnect(userInitiated = false)
        }
    }

    private val disconnected = object : EventHandler<DisconnectedEventArgs> {
        override fun handleEvent(sender: Any, e: DisconnectedEventArgs) {
            logger.debug("EventHandler `disconnected` called")

            val connectionRelease = connection.getAndSet(null)
            if (connectionRelease == null) {
                return
            }

            isConnected = false

            if (!e.isUserInitiated && !isExpectDisconnection) {
                servers.tryMark(connectionRelease.currentEndPoint, connectionRelease.protocolTypes, ServerQuality.BAD)
            }

            sessionID = null
            steamID = null

            connectionRelease.netMsgReceived.removeEventHandler(netMsgReceived)
            connectionRelease.connected.removeEventHandler(connected)
            connectionRelease.disconnected.removeEventHandler(this)

            heartBeatFunc.stop()

            onClientDisconnected(userInitiated = e.isUserInitiated || isExpectDisconnection)
        }
    }

    init {
        heartBeatFunc = ScheduledFunction(scope, 5000L) {
            send(
                ClientMsgProtobuf<CMsgClientHeartBeat.Builder>(
                    CMsgClientHeartBeat::class.java,
                    EMsg.ClientHeartBeat
                )
            )
        }
    }

    /**
     * Connects this client to a Steam3 server. This begins the process of connecting and encrypting the data channel
     * between the client and the server. Results are returned asynchronously in a [ConnectedCallback]. If the
     * server that SteamKit attempts to connect to is down, a [DisconnectedCallback] will be posted instead.
     * JavaSteam will not attempt to reconnect to Steam, you must handle this callback and call Connect again preferably
     * after a short delay.
     *
     * @param cmServer The [ServerRecord] of the CM server to connect to. If **null** JavaSteam will randomly select a CM server from its internal list.
     */
    @JvmOverloads
    fun connect(cmServer: ServerRecord? = null) {
        var server: ServerRecord? = cmServer

        synchronized(connectionLock) {
            try {
                disconnect(userInitiated = true)

                assert(connection.get() == null)

                isExpectDisconnection = false

                if (server == null) {
                    server = servers.getNextServerCandidate(configuration.protocolTypes)
                }

                val newConnection = createConnection(server!!.protocolTypes)
                val connectionRelease = connection.getAndSet(newConnection)
                require(connectionRelease == null) {
                    "Connection was set during a connect, did you call CMClient.Connect() on multiple threads?"
                }

                connection.get()!!.netMsgReceived.addEventHandler(netMsgReceived)
                connection.get()!!.connected.addEventHandler(connected)
                connection.get()!!.disconnected.addEventHandler(disconnected)
                connection.get()!!.connect(server.endpoint, connectionTimeout.toInt())
            } catch (e: Exception) {
                logger.debug("Failed to connect to Steam network", e)
                onClientDisconnected(userInitiated = false)
            }
        }
    }

    /**
     * Disconnects this client.
     */
    fun disconnect() {
        disconnect(userInitiated = true)
    }

    private fun disconnect(userInitiated: Boolean) {
        synchronized(connectionLock) {
            heartBeatFunc.stop()

            // Connection implementations are required to issue the Disconnected callback before Disconnect() returns
            connection.get()?.disconnect(userInitiated)
        }
    }

    /**
     * Sends the specified client message to the server. This method automatically assigns the correct SessionID and
     * SteamID of the message.
     *
     * @param msg The client message to send.
     */
    fun send(msg: IClientMsg) {
        this.sessionID?.let {
            msg.sessionID = it
        }

        this.steamID?.let {
            msg.steamID = it
        }

        val serialized = msg.serialize()

        try {
            debugNetworkListener?.onOutgoingNetworkMessage(msg.getMsgType(), serialized)
        } catch (e: Exception) {
            logger.debug("DebugNetworkListener threw an exception", e)
        }

        // we'll swallow any network failures here because they will be thrown later
        // on the network thread, and that will lead to a disconnect callback
        // down the line
        connection.get()?.send(serialized)
    }

    /**
     * Called when a client message is received from the network.
     * @param packetMsg The packet message.
     */
    protected open fun onClientMsgReceived(packetMsg: IPacketMsg?): Boolean {
        if (packetMsg == null) {
            logger.debug("Packet message failed to parse, shutting down connection")
            disconnect(userInitiated = false)
            return false
        }

        // Multi message gets logged down the line after it's decompressed
        if (packetMsg.getMsgType() != EMsg.Multi) {
            try {
                debugNetworkListener?.onIncomingNetworkMessage(packetMsg.msgType, packetMsg.data)
            } catch (e: Exception) {
                logger.debug("debugNetworkListener threw an exception", e)
            }
        }

        when (packetMsg.getMsgType()) {
            EMsg.Multi -> handleMulti(packetMsg)
            EMsg.ClientLogOnResponse -> handleLogOnResponse(packetMsg) // we handle this to get the SteamID/SessionID and to set up heartbeating
            EMsg.ClientLoggedOff -> handleLoggedOff(packetMsg) // to stop heartbeating when we get logged off
            EMsg.ClientServerUnavailable -> handleServerUnavailable(packetMsg)
            EMsg.ClientSessionToken -> handleSessionToken(packetMsg)
            else -> Unit
        }

        return true
    }

    /**
     * Called when the client is securely isConnected to Steam3.
     */
    protected open fun onClientConnected() {
        val request = ClientMsgProtobuf<SteammessagesClientserverLogin.CMsgClientHello.Builder>(
            SteammessagesClientserverLogin.CMsgClientHello::class.java,
            EMsg.ClientHello
        )

        request.body.setProtocolVersion(MsgClientLogon.CurrentProtocol)

        send(msg = request)
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

    // Note: getPacketMsg is Static

    // region ClientMsg Handlers
    private fun handleMulti(packetMsg: IPacketMsg) {
        if (!packetMsg.isProto()) {
            logger.debug("HandleMulti got non-proto MsgMulti!!")
            return
        }

        val msgMulti = ClientMsgProtobuf<CMsgMulti.Builder>(CMsgMulti::class.java, packetMsg)

        var payload: ByteArray = msgMulti.body.messageBody.toByteArray()

        if (msgMulti.body.sizeUnzipped > 0) {
            try {
                val gzin = GZIPInputStream(ByteArrayInputStream(payload))
                val baos = ByteArrayOutputStream()

                var res = 0
                val buf = ByteArray(1024)
                while (res >= 0) {
                    res = gzin.read(buf, 0, buf.size)
                    if (res > 0) {
                        baos.write(buf, 0, res)
                    }
                }
                payload = baos.toByteArray()
            } catch (e: IOException) {
                logger.debug("HandleMulti encountered an exception when decompressing.", e)
                return
            }
        }

        try {
            BinaryReader(ByteArrayInputStream(payload)).use { br ->
                while (br.available() > 0) {
                    val subSize = br.readInt()
                    val subData = br.readBytes(subSize)

                    if (!onClientMsgReceived(getPacketMsg(subData))) {
                        break
                    }
                }
            }
        } catch (e: IOException) {
            logger.error("error in handleMulti()", e)
        }
    }

    private fun handleLogOnResponse(packetMsg: IPacketMsg) {
        if (!packetMsg.isProto()) {
            // a non-proto ClientLogonResponse can come in as a result of connecting but never sending a ClientLogon
            // in this case, it always fails, so we don't need to do anything special here
            logger.debug("Got non-proto logon response, this is indicative of no logon attempt after connecting.")
            return
        }

        val logonResp = ClientMsgProtobuf<CMsgClientLogonResponse.Builder>(
            CMsgClientLogonResponse::class.java,
            packetMsg
        )
        val logonResponse = EResult.from(logonResp.body.eresult)

        logger.debug("handleLogOnResponse got response: $logonResponse")

        // Note: Sometimes if you sign in too many times, steam may confuse "InvalidPassword" with "RateLimitExceeded"
        if (logonResponse == EResult.OK) {
            sessionID = logonResp.protoHeader.clientSessionid
            steamID = SteamID(logonResp.protoHeader.steamid)

            cellID = logonResp.body.cellId
            publicIP = NetHelpers.getIPAddress(logonResp.body.publicIp)
            ipCountryCode = logonResp.body.ipCountryCode

            val hbDelay = logonResp.body.legacyOutOfGameHeartbeatSeconds

            // restart heartbeat
            heartBeatFunc.stop()
            heartBeatFunc.setDelay(hbDelay * 1000L)
            heartBeatFunc.start()
        } else if (logonResponse == EResult.TryAnotherCM || logonResponse == EResult.ServiceUnavailable) {
            connection.get()?.let { connection ->
                servers.tryMark(connection.currentEndPoint, connection.protocolTypes, ServerQuality.BAD)
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

        if (packetMsg.isProto()) {
            val logoffMsg = ClientMsgProtobuf<CMsgClientLoggedOff.Builder>(CMsgClientLoggedOff::class.java, packetMsg)
            val logoffResult = EResult.from(logoffMsg.body.eresult)

            logger.debug("handleLoggedOff got $logoffResult")

            if (logoffResult == EResult.TryAnotherCM || logoffResult == EResult.ServiceUnavailable) {
                connection.get()!!.let { connection ->
                    servers.tryMark(connection.currentEndPoint, connection.protocolTypes, ServerQuality.BAD)
                }
            }
        } else {
            logger.debug("handleLoggedOff got unexpected response: " + packetMsg.msgType)
        }
    }

    private fun handleServerUnavailable(packetMsg: IPacketMsg?) {
        val msgServerUnavailable = ClientMsg<MsgClientServerUnavailable>(
            MsgClientServerUnavailable::class.java,
            packetMsg
        )

        logger.debug(
            "A server of type ${msgServerUnavailable.body.eServerTypeUnavailable} " +
                "was not available for request: ${EMsg.from(msgServerUnavailable.body.eMsgSent)}"
        )

        disconnect(userInitiated = false)
    }

    private fun handleSessionToken(packetMsg: IPacketMsg?) {
        val sessToken = ClientMsgProtobuf<CMsgClientSessionToken.Builder>(
            CMsgClientSessionToken::class.java,
            packetMsg
        )

        sessionToken = sessToken.body.token
    }
    // endregion

    /**
     * Gets a value indicating whether isConnected and connection is not connected to the remote CM server.
     * Inverse alternative to [CMClient.isConnected]
     * @return **true** is this instance is disconnected, otherwise, **false**.
     */
    // JavaSteam addition: "since the client can technically not be connected but still have a connection"
    @Suppress("unused")
    fun isDisconnected(): Boolean = !isConnected && connection.get() == null

    companion object {
        private val logger: Logger = LogManager.getLogger(CMClient::class.java)

        @JvmStatic
        fun getPacketMsg(data: ByteArray): IPacketMsg? {
            if (data.size < 4) {
                logger.debug(
                    "PacketMsg too small to contain a message, " +
                        "was only ${data.size} bytes. Message: ${Strings.toHex(data)}"
                )
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
                EMsg.ChannelEncryptRequest, EMsg.ChannelEncryptResponse, EMsg.ChannelEncryptResult -> try {
                    return PacketMsg(eMsg, data)
                } catch (e: IOException) {
                    logger.debug("Exception deserializing emsg " + eMsg + " (" + MsgUtil.isProtoBuf(rawEMsg) + ").", e)
                }

                else -> Unit
            }

            try {
                return if (MsgUtil.isProtoBuf(rawEMsg)) {
                    // if the emsg is flagged, we're a proto message
                    PacketClientMsgProtobuf(eMsg, data)
                } else {
                    // otherwise we're a struct message
                    PacketClientMsg(eMsg, data)
                }
            } catch (e: IOException) {
                logger.debug("Exception deserializing emsg $eMsg (${MsgUtil.isProtoBuf(rawEMsg)}).", e)
                return null
            }
        }
    }
}
