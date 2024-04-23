package `in`.dragonbra.javasteam.networking.steam3

import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer

internal class WebSocketCMClient(
    serverUri: URI,
    timeout: Int,
    private val listener: WSListener,
) : WebSocketClient(serverUri, Draft_6455(), null, timeout) {

    override fun onOpen(handshakeData: ServerHandshake) {
        listener.onOpen()
    }

    override fun onMessage(message: String) {
        // ignore string messages
        logger.debug("got string message: $message")
    }

    override fun onMessage(bytes: ByteBuffer) {
        listener.let {
            val data = ByteArray(bytes.remaining())
            bytes[data]
            it.onData(data)
        }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        listener.onClose(remote)
    }

    override fun onError(ex: Exception) {
        listener.onError(ex)
    }

    internal interface WSListener {
        fun onData(data: ByteArray)
        fun onClose(remote: Boolean)
        fun onError(ex: Exception)
        fun onOpen()
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(WebSocketCMClient::class.java)
    }
}
