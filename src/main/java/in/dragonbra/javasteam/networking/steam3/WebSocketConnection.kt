package `in`.dragonbra.javasteam.networking.steam3

import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.Volatile

class WebSocketConnection : Connection(), WebSocketCMClient.WSListener {

    private val client = AtomicReference<WebSocketCMClient>(null)

    @Volatile
    private var userInitiated = false

    override var currentEndPoint: InetSocketAddress? = null

    override val localIP: InetAddress?
        get() = client.get()!!.localSocketAddress.address

    override val protocolTypes: ProtocolTypes
        get() = ProtocolTypes.WEB_SOCKET

    override fun connect(endPoint: InetSocketAddress, timeout: Int) {
        logger.debug("Connecting to $endPoint...")
        val newClient = WebSocketCMClient(getUri(endPoint), timeout, this)
        val oldClient = client.getAndSet(newClient)

        oldClient?.let {
            logger.debug("Attempted to connect while already connected. Closing old connection...")
            it.close()
        }

        currentEndPoint = endPoint

        newClient.connect()
    }

    override fun disconnect(userInitiated: Boolean) {
        disconnectCore(userInitiated)
    }

    override fun send(data: ByteArray) {
        try {
            client.get()?.send(data)
        } catch (e: Exception) {
            logger.debug("Exception while sending data", e)
            disconnectCore(false)
        }
    }

    private fun disconnectCore(userInitiated: Boolean) {
        val oldClient = client.getAndSet(null)

        oldClient?.let {
            it.close()
            this.userInitiated = userInitiated
        }

        currentEndPoint = null
    }

    override fun onData(data: ByteArray) {
        if (data.isNotEmpty()) {
            onNetMsgReceived(NetMsgEventArgs(data, currentEndPoint!!))
        }
    }

    override fun onClose(remote: Boolean) {
        onDisconnected(userInitiated && !remote)
    }

    override fun onError(ex: Exception) {
        logger.debug("error in websocket", ex)
    }

    override fun onOpen() {
        logger.debug("Connected to $currentEndPoint")
        onConnected()
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(WebSocketConnection::class.java)

        private fun getUri(address: InetSocketAddress): URI =
            URI.create("wss://" + address.hostString + ":" + address.port + "/cmsocket/")
    }
}
