package `in`.dragonbra.javasteam.networking.steam3

import `in`.dragonbra.javasteam.util.event.Event
import `in`.dragonbra.javasteam.util.event.EventArgs
import java.net.InetAddress
import java.net.InetSocketAddress

// TODO audit 'networking.steam3' with SK

/**
 * @author lngtr
 * @since 2018-02-20
 */
abstract class Connection {
    /**
     * Occurs when a net message is received over the network.
     */
    val netMsgReceived: Event<NetMsgEventArgs> = Event()

    /**
     * Occurs when the physical connection is established.
     */
    val connected: Event<EventArgs> = Event<EventArgs>()

    /**
     * Occurs when the physical connection is broken.
     */
    val disconnected: Event<DisconnectedEventArgs> = Event()

    /**
     * TODO kDoc
     */
    fun onNetMsgReceived(e: NetMsgEventArgs) {
        netMsgReceived.handleEvent(this, e)
    }

    /**
     * TODO kDoc
     */
    fun onConnected() {
        connected.handleEvent(this, null)
    }

    /**
     * TODO kDoc
     */
    fun onDisconnected(e: Boolean) {
        disconnected.handleEvent(this, DisconnectedEventArgs(e))
    }

    /**
     * Connects to the specified end point.
     * @param endPoint The end point to connect to.
     * @param timeout  Timeout in milliseconds
     */
    abstract fun connect(endPoint: InetSocketAddress, timeout: Int)

    /**
     * Connects to the specified end point.
     * @param endPoint The end point to connect to. Defaults to 5000 millisecond timeout.
     */
    fun connect(endPoint: InetSocketAddress) {
        connect(endPoint, 5000)
    }

    /**
     * Disconnects this instance.
     * @param userInitiated If true, this disconnection attempt was initated by a consumer.
     */
    abstract fun disconnect(userInitiated: Boolean)

    /**
     * Sends the specified data packet.
     * @param data The data packet to send.
     */
    abstract fun send(data: ByteArray)

    /**
     * Gets the local IP.
     * @return The local IP.
     */
    abstract val localIP: InetAddress?

    /**
     * TODO kDoc
     */
    abstract val currentEndPoint: InetSocketAddress?

    /**
     * @return The type of communication protocol that this connection uses.
     */
    abstract val protocolTypes: ProtocolTypes
}
