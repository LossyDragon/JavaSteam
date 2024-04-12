package `in`.dragonbra.javasteam.networking.steam3

import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

internal class WebSocketCMClient(
    private val serverUri: URI,
    timeout: Int,
    private val listener: WSListener,
) :
    WebSocketListener() {

    private lateinit var webSocket: WebSocket

    val client: OkHttpClient

    val localSocketAddress: InetSocketAddress?
        get() = null // TODO possible?

    init {
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeout.toLong(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)

        client = builder.build()
    }

    fun send(data: ByteArray) {
        webSocket.send(data.toByteString())
    }

    fun close() {
        webSocket.close(1000, "Closing WebSocket connection")
    }

    fun connect() {
        val request = Request.Builder()
            .url(serverUri.toString())
            .build()

        webSocket = client.newWebSocket(request, this)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        listener.onClose(false)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        listener.onClose(true)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        listener.onError(t)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        // ignore string messages
        logger.debug("got string message: $text")
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        val data = ByteArray(bytes.asByteBuffer().remaining())
        bytes.asByteBuffer()[data]
        listener.onData(data)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        listener.onOpen()
    }

    internal interface WSListener {
        fun onData(data: ByteArray?)

        fun onClose(remote: Boolean)

        fun onError(t: Throwable?)

        fun onOpen()
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(WebSocketCMClient::class.java)
    }
}
