package `in`.dragonbra.javasteam.networking.steam3

import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import `in`.dragonbra.javasteam.util.stream.BinaryReader
import `in`.dragonbra.javasteam.util.stream.BinaryWriter
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.Volatile

/**
 * @author lngtr
 * @since 2018-02-21
 */
class TcpConnection : Connection() {

    private var socket: Socket? = null

    private var netWriter: BinaryWriter? = null

    private var netReader: BinaryReader? = null

    private var netThread: Thread? = null

    private var netLoop: NetLoop? = null

    private val netLock = Any()

    override val localIP: InetAddress?
        get() {
            synchronized(netLock) {
                if (socket == null) {
                    return null
                }
                return socket!!.localAddress
            }
        }

    override var currentEndPoint: InetSocketAddress? = null

    override val protocolTypes: ProtocolTypes
        get() = ProtocolTypes.TCP

    private fun shutdown() {
        try {
            if (socket!!.isConnected) {
                socket!!.shutdownInput()
                socket!!.shutdownOutput()
            }
        } catch (e: IOException) {
            logger.debug(e)
        }
    }

    private fun connectionCompleted(success: Boolean) {
        if (!success) {
            logger.debug("Timed out while connecting to $currentEndPoint")
            release(false)
            return
        }

        logger.debug("Connected to $currentEndPoint")

        try {
            synchronized(netLock) {
                netReader = BinaryReader(socket!!.getInputStream())
                netWriter = BinaryWriter(socket!!.getOutputStream())

                netLoop = NetLoop()
                netThread = Thread(netLoop, "TcpConnection Thread")
                currentEndPoint = InetSocketAddress(socket!!.inetAddress, socket!!.port)
            }

            netThread!!.start()

            onConnected()
        } catch (e: IOException) {
            logger.debug("Exception while setting up connection to $currentEndPoint", e)
            release(false)
        }
    }

    private fun release(userRequestedDisconnect: Boolean) {
        synchronized(netLock) {
            if (netWriter != null) {
                try {
                    netWriter!!.close()
                } catch (ignored: IOException) {
                }
                netWriter = null
            }

            if (netReader != null) {
                try {
                    netReader!!.close()
                } catch (ignored: IOException) {
                }
                netReader = null
            }

            if (socket != null) {
                try {
                    socket!!.close()
                } catch (ignored: IOException) {
                }
                socket = null
            }
        }

        onDisconnected(userRequestedDisconnect)
    }

    override fun connect(endPoint: InetSocketAddress, timeout: Int) {
        synchronized(netLock) {
            currentEndPoint = endPoint
            try {
                logger.debug("Connecting to $currentEndPoint...")
                socket = Socket()
                socket!!.connect(endPoint, timeout)

                connectionCompleted(true)
            } catch (e: IOException) {
                logger.debug("Socket exception while completing connection request to $currentEndPoint", e)
                connectionCompleted(false)
            }
        }
    }

    override fun disconnect(userInitiated: Boolean) {
        synchronized(netLock) {
            netLoop?.stop(userInitiated)
        }
    }

    @Throws(IOException::class)
    private fun readPacket(): ByteArray {
        val packetLen = netReader!!.readInt()
        val packetMagic = netReader!!.readInt()

        if (packetMagic != MAGIC) {
            throw IOException("Got a packet with invalid magic!")
        }

        return netReader!!.readBytes(packetLen)
    }

    override fun send(data: ByteArray) {
        synchronized(netLock) {
            if (socket == null) {
                logger.debug("Attempting to send client data when not connected.")
                return
            }
            try {
                netWriter!!.writeInt(data.size)
                netWriter!!.writeInt(MAGIC)
                netWriter!!.write(data)
            } catch (e: IOException) {
                logger.debug("Socket exception while writing data.", e)

                // looks like the only way to detect a closed connection is to try and write to it
                // afaik read also throws an exception if the connection is open but there is nothing to read
                netLoop?.stop(false)
            }
        }
    }

    // this is now a steamkit meme
    /**
     * Nets the loop.
     */
    private inner class NetLoop : Runnable {
        @Volatile
        private var cancelRequested = false

        @Volatile
        private var userRequested = false

        fun stop(userRequested: Boolean) {
            this.userRequested = userRequested
            cancelRequested = true
        }

        override fun run() {
            while (!cancelRequested) {
                try {
                    Thread.sleep(POLL_MS.toLong())
                } catch (e: InterruptedException) {
                    logger.debug("Thread interrupted", e)
                }

                if (cancelRequested) {
                    break
                }

                var canRead: Boolean

                try {
                    canRead = netReader!!.available() > 0
                } catch (e: IOException) {
                    logger.debug("Socket exception while polling", e)
                    break
                }

                if (!canRead) {
                    // nothing to read yet
                    continue
                }

                var packData: ByteArray

                try {
                    packData = readPacket()

                    onNetMsgReceived(NetMsgEventArgs(packData, currentEndPoint!!))
                } catch (e: IOException) {
                    logger.debug("Socket exception occurred while reading packet", e)
                    break
                }
            }

            if (cancelRequested) {
                shutdown()
            }

            release(cancelRequested && userRequested)
        }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(TcpConnection::class.java)

        private const val MAGIC = 0x31305456 // "VT01"

        private const val POLL_MS = 100
    }
}
