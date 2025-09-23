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
import `in`.dragonbra.javasteam.networking.steam3.NetMsgEventArgs
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientSessionToken
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientHeartBeat
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientHello
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientLoggedOff
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientLogonResponse
import `in`.dragonbra.javasteam.steam.discovery.ServerQuality
import `in`.dragonbra.javasteam.steam.discovery.ServerRecord
import `in`.dragonbra.javasteam.steam.discovery.SmartCMServerList
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.types.SteamID.Companion.toSteamID
import `in`.dragonbra.javasteam.util.IDebugNetworkListener
import `in`.dragonbra.javasteam.util.MsgUtil
import `in`.dragonbra.javasteam.util.NetHelpers.toInetAddress
import `in`.dragonbra.javasteam.util.NetHookNetworkListener
import `in`.dragonbra.javasteam.util.Strings
import `in`.dragonbra.javasteam.util.event.EventArgs
import `in`.dragonbra.javasteam.util.event.EventHandler
import `in`.dragonbra.javasteam.util.event.ScheduledFunction
import `in`.dragonbra.javasteam.util.log.LogManager
import okio.Buffer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*

/**
 * This base client handles the underlying connection to a CM server. This class should not be use directly, but through the [SteamClient] class.
 * @constructor Initializes a new instance of the [CMClient] class with a specific configuration.
 * @param configuration The configuration to use for this client.
 * @param identifier A specific identifier to be used to uniquely identify this instance.
 */
@Suppress("unused")
abstract class CMClient
@Throws(IllegalStateException::class)
constructor(
    val configuration: SteamConfiguration,
    val identifier: String,
) {

    companion object {
        private val logger = LogManager.getLogger(CMClient::class.java)

        @JvmStatic
        fun getPacketMsg(data: ByteArray): IPacketMsg? {
            if (data.size < 4) {
                logger.debug(
                    "PacketMsg too small to contain a message, " +
                        "was only ${data.size} bytes. Message: ${Strings.toHex(data)}"
                )
                return null
            }

            val rawEMsg = try {
                Buffer().write(data).readInt()
            } catch (e: Exception) {
                logger.debug("Exception while getting EMsg code", e)
                0
            }
            val eMsg = MsgUtil.getMsg(rawEMsg)

            when (eMsg) {
                // certain message types are always MsgHdr
                EMsg.ChannelEncryptRequest,
                EMsg.ChannelEncryptResponse,
                EMsg.ChannelEncryptResult,
                -> return PacketMsg(eMsg, data)

                else -> Unit
            }

            try {
                if (MsgUtil.isProtoBuf(rawEMsg)) {
                    // if the emsg is flagged, we're a proto message
                    return PacketClientMsgProtobuf(eMsg, data)
                } else {
                    // otherwise we're a struct message
                    return PacketClientMsg(eMsg, data)
                }
            } catch (ex: Exception) {
                logger.error("Exception deserializing emsg $eMsg (${MsgUtil.isProtoBuf(rawEMsg)})", ex)
                return null
            }
        }
    }

    /**
     * Bootstrap list of CM servers.
     */
    val servers: SmartCMServerList
        get() = configuration.serverList

    /**
     * Returns the local IP of this client.
     */
    val localIP: InetAddress?
        get() = connection?.localIP

    /**
     * Returns the current endpoint this client is connected to.
     */
    val currentEndPoint: InetSocketAddress?
        get() = connection?.currentEndPoint

    /**
     * Gets the public IP address of this client. This value is assigned after a logon attempt has succeeded.
     * This value will be <c>null</c> if the client is logged off of Steam.
     */
    var publicIP: InetAddress? = null
        private set

    /**
     * Gets the country code of our public IP address according to Steam. This value is assigned after a logon attempt has succeeded.
     * This value will be <c>null</c> if the client is logged off of Steam.
     */
    var ipCountryCode: String? = null
        private set

    /**
     * Gets the universe of this client.
     */
    val universe: EUniverse
        get() = configuration.universe

    /**
     * Gets a value indicating whether this instance is connected to the remote CM server.
     * <c>true</c> if this instance is connected; otherwise, <c>false</c>.
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
     * This value will be <c>null</c> if the client is logged off of Steam.
     */
    var cellID: Int? = null
        private set

    /**
     * Gets the session ID of this client. This value is assigned after a logon attempt has succeeded.
     * This value will be <c>null</c> if the client is logged off of Steam.
     */
    var sessionID: Int? = null
        private set

    /**
     * Gets the SteamID of this client. This value is assigned after a logon attempt has succeeded.
     * This value will be <c>null</c> if the client is logged off of Steam.
     */
    var steamID: SteamID? = null
        private set

    /**
     * Gets or Sets the connection timeout used when connecting to the Steam server.
     */
    val connectionTimeout: Long
        get() = configuration.connectionTimeout

    /**
     * Gets or sets the network listening interface. Use this for debugging only.
     * For your convenience, you can use [NetHookNetworkListener] class.
     */
    var debugNetworkListener: IDebugNetworkListener? = null

    var expectDisconnection: Boolean = false // Should be internal

    // connection lock around the setup and tear down of the connection task
    val connectionLock = Any()

    @Volatile
    private var connection: Connection? = null

    private var heartBeatFunc: ScheduledFunction? = null

    init {
        require(identifier.isNotBlank()) { "Identifier must not be blank" }

        heartBeatFunc = ScheduledFunction({
            val heartbeat = ClientMsgProtobuf<CMsgClientHeartBeat.Builder>(
                CMsgClientHeartBeat::class.java,
                EMsg.ClientHeartBeat
            )
            heartbeat.getBody().setSendReply(true) // Ping Pong
            send(heartbeat)
        }, 5000L)
    }

    /**
     * Connects this client to a Steam3 server.
     * This begins the process of connecting and encrypting the data channel between the client and the server.
     * Results are returned asynchronously in a <see cref="SteamClient.ConnectedCallback"/>.
     * If the server that JavaSteam attempts to connect to is down, a [DisconnectedCallback] will be posted instead.
     * JavaSteam will not attempt to reconnect to Steam, you must handle this callback and call Connect again
     * preferably after a short delay.
     */
    @JvmOverloads
    fun connect(cmServer: ServerRecord? = null) {
        synchronized(connectionLock) {
            try {
                disconnect(userInitiated = true)
                require(connection == null) {
                    "Connection is not null"
                }

                expectDisconnection = false

                val recordTask = cmServer ?: servers.getNextServerCandidate(configuration.protocolTypes)

                if (recordTask == null) {
                    logger.error("No CM servers available to connect to")
                    onClientDisconnected(false)
                    return
                }

                connection = createConnection(cmServer!!.protocolTypes)
                connection!!.getNetMsgReceived().addEventHandler(netMsgReceived)
                connection!!.getConnected().addEventHandler(connected)
                connection!!.getDisconnected().addEventHandler(disconnected)
                logger.debug(
                    "Connecting to ${cmServer.endpoint} with protocol ${cmServer.protocolTypes}, " +
                        "and with connection impl ${connection!!.javaClass.getSimpleName()}",
                )
                connection!!.connect(cmServer.endpoint, connectionTimeout.toInt())
            } catch (e: Exception) {
                logger.debug("Failed to connect to Steam network", e)
                onClientDisconnected(false)
            }
        }
    }

    /**
     * Disconnects this client.
     */
    fun disconnect() {
        disconnect(true)
    }

    private fun disconnect(userInitiated: Boolean) {
        synchronized(connectionLock) {
            heartBeatFunc?.stop()

            // Connection implementations are required to issue the Disconnected callback before Disconnect() returns
            connection?.disconnect(userInitiated)
            require(connection == null) {
                "Connection was not released in disconnect."
            }
        }
    }

    /**
     * Sends the specified client message to the server.
     * This method automatically assigns the correct SessionID and SteamID of the message.
     * @param msg The client message to send.
     */
    fun send(msg: IClientMsg) {
        require(isConnected) {
            "Send() was called while not connected to Steam."
        }

        sessionID?.let { msg.sessionID = it }
        steamID?.let { msg.steamID = it }

        val serialized = msg.serialize()

        debugNetworkListener?.onOutgoingNetworkMessage(msg.msgType, serialized)

        // we'll swallow any network failures here because they will be thrown later
        // on the network thread, and that will lead to a disconnect callback
        // down the line

        connection?.send(serialized)
    }

    /**
     * Called when a client message is received from the network.
     * @param packetMsg The packet message.
     */
    protected open fun onClientMsgReceived(packetMsg: IPacketMsg?): Boolean {
        if (packetMsg == null) {
            logger.error("Packet message failed to parse, shutting down connection")
            disconnect(userInitiated = false)
            return false
        }

        // Multi message gets logged down the line after it's decompressed
        if (packetMsg.msgType != EMsg.Multi) {
            debugNetworkListener?.onIncomingNetworkMessage(packetMsg.msgType, packetMsg.data)
        }

        when (packetMsg.msgType) {
            EMsg.Multi -> handleMulti(packetMsg)
            EMsg.ClientLogOnResponse -> handleLogOnResponse(packetMsg) // we handle this to get the SteamID/SessionID and to setup heartbeating.
            EMsg.ClientLoggedOff -> handleLoggedOff(packetMsg) // to stop heartbeating when we get logged off
            EMsg.ClientServerUnavailable -> handleServerUnavailable(packetMsg)
            EMsg.ClientSessionToken -> handleSessionToken(packetMsg) // am session token
            else -> Unit
        }

        return true
    }

    /**
     * Called when the client is securely connected to Steam3.
     */
    protected open fun onClientConnected() {
        val request = ClientMsgProtobuf<CMsgClientHello.Builder>(
            CMsgClientHello::class.java,
            EMsg.ClientHello
        ).apply {
            body.protocolVersion = MsgClientLogon.CurrentProtocol
        }

        send(request)
    }

    /**
     * Called when the client is physically disconnected from Steam3.
     * @param userInitiated whether the disconnect was initialized by the client
     */
    protected open fun onClientDisconnected(userInitiated: Boolean) {
    }

    private fun createConnection(protocol: EnumSet<ProtocolTypes>): Connection {
        val connectionFactory = configuration.connectionFactory
        val connection = connectionFactory.createConnection(configuration, protocol)

        if (connection == null) {
            logger.error(String.format("Connection factory returned null connection for protocols %s", protocol))
            throw IllegalArgumentException("Connection factory returned null connection.")
        }

        return connection
    }

    /**
     * Debug method - Do Not Use Directly
     */
    @Suppress("unused")
    fun receiveTestPacketMsg(packetMsg: IPacketMsg) {
        onClientMsgReceived(packetMsg)
    }

    /**
     * Debug method - Do Not Use Directly
     */
    @Suppress("unused")
    fun setIsConnected(value: Boolean) {
        isConnected = value
    }

    private val netMsgReceived: EventHandler<NetMsgEventArgs> = EventHandler { _, e ->
        onClientMsgReceived(getPacketMsg(e.data))
    }

    private val connected: EventHandler<EventArgs> = EventHandler { _, _ ->
        logger.debug("EventHandler `connected` called")

        requireNotNull(connection) {
            "No connection object after connecting."
        }
        requireNotNull(connection?.currentEndPoint) {
            "No connection endpoint after connecting - cannot update server list"
        }
        servers.tryMark(
            endPoint = connection!!.getCurrentEndPoint(),
            protocolTypes = connection!!.getProtocolTypes(),
            quality = ServerQuality.GOOD
        )

        isConnected = true

        try {
            onClientConnected()
        } catch (ex: java.lang.Exception) {
            logger.error("Unhandled exception after connecting: ", ex)
            disconnect(userInitiated = false)
        }
    }

    private val disconnected: EventHandler<DisconnectedEventArgs> = object : EventHandler<DisconnectedEventArgs> {
        override fun handleEvent(sender: Any?, e: DisconnectedEventArgs) {
            logger.debug(
                "EventHandler `disconnected` called. " +
                    "User Initiated: ${e.isUserInitiated}, Expected Disconnection: $expectDisconnection"
            )

            isConnected = false

            if (!e.isUserInitiated && !expectDisconnection) {
                requireNotNull(connection?.currentEndPoint) {
                    "No connection endpoint while disconnecting - cannot update server list"
                }
                servers.tryMark(
                    endPoint = connection!!.getCurrentEndPoint(),
                    protocolTypes = connection!!.getProtocolTypes(),
                    quality = ServerQuality.BAD
                )
            }

            sessionID = null
            steamID = null

            connection!!.getNetMsgReceived().removeEventHandler(netMsgReceived)
            connection!!.getConnected().removeEventHandler(connected)
            connection!!.getDisconnected().removeEventHandler(this)

            connection = null

            heartBeatFunc!!.stop()

            onClientDisconnected(userInitiated = e.isUserInitiated || expectDisconnection)
        }
    }

    // region ClientMsg Handlers
    private fun handleMulti(packetMsg: IPacketMsg) {
        if (!packetMsg.isProto) {
            logger.debug("HandleMulti got non-proto MsgMulti!!")
            return
        }

        TODO()
    }

    private fun handleLogOnResponse(packetMsg: IPacketMsg) {
        if (!packetMsg.isProto) {
            // a non-proto ClientLogonResponse can come in as a result of connecting but never sending a ClientLogon
            // in this case, it always fails, so we don't need to do anything special here
            logger.debug("Got non-proto logon response, this is indicative of no logon attempt after connecting.")
            return
        }

        val logonResp = ClientMsgProtobuf<CMsgClientLogonResponse.Builder>(
            CMsgClientLogonResponse::class.java,
            packetMsg
        )
        val logonResult = EResult.from(logonResp.body.eresult)

        if (logonResult == EResult.OK) {
            sessionID = logonResp.protoHeader.clientSessionid
            steamID = logonResp.protoHeader.steamid.toSteamID()

            cellID = logonResp.body.cellId
            publicIP = logonResp.body.publicIp.toInetAddress()
            ipCountryCode = logonResp.body.ipCountryCode

            val hbDelay = logonResp.body.legacyOutOfGameHeartbeatSeconds

            // restart heartbeat
            heartBeatFunc!!.stop()
            heartBeatFunc!!.delay = hbDelay.toLong()
            heartBeatFunc!!.stop()
        } else if (logonResult == EResult.TryAnotherCM || logonResult == EResult.ServiceUnavailable) {
            connection?.currentEndPoint.let { currentEndPoint ->
                servers.tryMark(currentEndPoint, connection!!.protocolTypes, ServerQuality.BAD)
            }
        }
    }

    private fun handleLoggedOff(packetMsg: IPacketMsg) {
        sessionID = null
        steamID = null

        cellID = null
        publicIP = null
        ipCountryCode = null

        heartBeatFunc?.stop()

        if (packetMsg.isProto) {
            val logoffMsg = ClientMsgProtobuf<CMsgClientLoggedOff.Builder>(
                CMsgClientLoggedOff::class.java,
                packetMsg
            )
            val logoffResult = EResult.from(logoffMsg.body.eresult)

            if (logoffResult == EResult.TryAnotherCM || logoffResult == EResult.ServiceUnavailable) {
                requireNotNull(connection) {
                    "No connection object during ClientLoggedOff."
                }
                requireNotNull(connection!!.currentEndPoint) {
                    "No connection endpoint during ClientLoggedOff - cannot update server list status"
                }
                servers.tryMark(connection!!.currentEndPoint, connection!!.protocolTypes, ServerQuality.BAD)
            }
        }
    }

    private fun handleServerUnavailable(packetMsg: IPacketMsg) {
        val msgServerUnavailable = ClientMsg(MsgClientServerUnavailable::class.java, packetMsg)

        logger.debug(
            "A server of type '${msgServerUnavailable.body.eServerTypeUnavailable}' " +
                "was not available for request: '${EMsg.from(msgServerUnavailable.body.eMsgSent)}'"
        )

        disconnect(userInitiated = false)
    }

    private fun handleSessionToken(packetMsg: IPacketMsg) {
        val sessToken = ClientMsgProtobuf<CMsgClientSessionToken.Builder>(
            CMsgClientSessionToken::class.java,
            packetMsg
        )

        sessionToken = sessToken.body.token
    }
    // endregion
}
