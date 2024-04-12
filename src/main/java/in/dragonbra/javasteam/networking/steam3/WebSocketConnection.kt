package `in`.dragonbra.javasteam.networking.steam3

import `in`.dragonbra.javasteam.util.ProxyWrapper
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.Volatile

class WebSocketConnection : Connection(), WebSocketCMClient.WSListener {

    private val client = AtomicReference<WebSocketCMClient?>(null)

    @Volatile
    private var userInitiated = false

    private var socketEndPoint: InetSocketAddress? = null

    override fun connect(endPoint: InetSocketAddress, timeout: Int, proxyWrapper: ProxyWrapper?) {
        logger.debug("Connecting to $endPoint...")

        val newClient = WebSocketCMClient(timeout, getUri(endPoint), this, proxyWrapper)
        val oldClient = client.getAndSet(newClient)

        if (oldClient != null) {
            logger.debug("Attempted to connect while already connected. Closing old connection...")
            oldClient.close()
        }

        socketEndPoint = endPoint

        newClient.connect()
    }

    override fun disconnect() {
        disconnectCore(true)
    }

    override fun send(data: ByteArray) {
        try {
            if (client.get() == null) {
                // If we're in the process of being disconnected using WebSocket,
                // and our client is still sending data to steam during that process.
                // Our `client` reference is most likely null and the exception doesn't handle it right.
                logger.debug("WebSocket client is null")
                return
            }

            client.get()!!.send(data)
        } catch (e: Exception) {
            logger.debug("Exception while sending data", e)
            disconnectCore(false)
        }
    }

    override fun getLocalIP(): InetAddress = client.get()!!.localSocketAddress!!.address

    override fun getCurrentEndPoint(): InetSocketAddress = socketEndPoint!!

    override fun getProtocolTypes(): ProtocolTypes = ProtocolTypes.WEB_SOCKET

    private fun disconnectCore(userInitiated: Boolean) {
        val oldClient = client.getAndSet(null)

        if (oldClient != null) {
            oldClient.close()
            this.userInitiated = userInitiated
        }

        socketEndPoint = null
    }

    override fun onData(data: ByteArray?) {
        if (data != null && data.isNotEmpty()) {
            onNetMsgReceived(NetMsgEventArgs(data, currentEndPoint))
        }
    }

    override fun onClose(remote: Boolean) {
        onDisconnected(userInitiated && !remote)
    }

    override fun onError(t: Throwable?) {
        logger.debug("error in websocket", t)
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
