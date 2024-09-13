package `in`.dragonbra.javasteam.networking.steam3

import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.stream.BinaryReader
import `in`.dragonbra.javasteam.util.stream.BinaryWriter
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.jvm.javaio.toOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.time.Duration.Companion.milliseconds

class TcpConnectionKt : Connection() {

    companion object {
        private const val MAGIC = 0x31305456 // "VT01"
        private val logger = LogManager.getLogger(TcpConnectionKt::class.java)
    }

    private var socket: Socket? = null
    private var netWriter: BinaryWriter? = null
    private var netReader: BinaryReader? = null
    private var netJob: Job? = null
    private val mutex = Mutex()
    private var userInitiated = false

    private var currentEndPoint: InetSocketAddress? = null

    private val selectorManager = ActorSelectorManager(Dispatchers.IO + SupervisorJob())

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun connect(endPoint: InetSocketAddress, timeout: Int) {
        scope.launch {
            mutex.withLock {
                try {
                    userInitiated = false

                    socket = aSocket(selectorManager).tcp().connect(endPoint.hostName, endPoint.port)
                    netReader = BinaryReader(socket!!.openReadChannel().toInputStream())
                    netWriter = BinaryWriter(socket!!.openWriteChannel().toOutputStream())

                    logger.debug("Connected to $endPoint")
                    currentEndPoint = InetSocketAddress(endPoint.hostName, endPoint.port)

                    onConnected()

                    // this is now a steamkit meme

                    /**
                     * Nets the loop.
                     */
                    netJob = launch(Dispatchers.IO) {
                        try {
                            while (isActive) {
                                delay(100.milliseconds)
                                readPacket()?.let { packetData ->
                                    onNetMsgReceived(NetMsgEventArgs(packetData, currentEndPoint))
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Error while reading", e)
                        } finally {
                            disconnect()
                            onDisconnected(userInitiated)
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error while connecting", e)
                    onDisconnected(userInitiated)
                }
            }
        }
    }

    override fun disconnect() {
        scope.launch {
            mutex.withLock {
                userInitiated = true
                netJob?.cancelAndJoin()
                socket?.close()
                selectorManager.close()
                scope.cancel()
                logger.debug("Disconnected from server")
            }
        }
    }

    override fun send(data: ByteArray) {
        scope.launch {
            mutex.withLock {
                netWriter!!.writeInt(data.size)
                netWriter!!.writeInt(MAGIC)
                netWriter!!.write(data)
                netWriter!!.flush()
            }
        }
    }

    override fun getLocalIP(): InetAddress? = (socket?.localAddress?.toJavaAddress() as? InetSocketAddress)?.address

    override fun getCurrentEndPoint(): InetSocketAddress? = currentEndPoint

    override fun getProtocolTypes(): ProtocolTypes = ProtocolTypes.TCP

    private fun readPacket(): ByteArray? {
        if (netReader == null) {
            throw NullPointerException("NetReader is null, socket closed")
        }

        if (netReader!!.available() <= 0) {
            // No data to read, skip this loop iteration
            return null
        }

        val packetLen = netReader!!.readInt()
        val packetMagic = netReader!!.readInt()

        if (packetMagic != MAGIC) {
            throw Exception("Got a packet with invalid magic!")
        }

        return netReader!!.readBytes(packetLen)
    }
}
