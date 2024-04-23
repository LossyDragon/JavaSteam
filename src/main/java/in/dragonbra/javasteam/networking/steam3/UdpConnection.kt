package `in`.dragonbra.javasteam.networking.steam3

import `in`.dragonbra.javasteam.enums.EUdpPacketType
import `in`.dragonbra.javasteam.generated.ChallengeData
import `in`.dragonbra.javasteam.generated.ConnectData
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import `in`.dragonbra.javasteam.util.stream.MemoryStream
import `in`.dragonbra.javasteam.util.stream.SeekOrigin
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.Volatile
import kotlin.math.min

/**
 * @author lngtr
 * @since 2018-03-01
 */
class UdpConnection : Connection() {

    /**
     * Contains information about the state of the connection, used to filter out packets that are
     * unexpected or not valid given the state of the connection.
     */
    @Volatile
    private var state: AtomicReference<State>

    private var netThread: Thread? = null

    private var netLoop: NetLoop? = null

    private var sock: DatagramSocket? = null

    private var timeout: Long = 0

    private var nextResend: Long = 0

    private var remoteConnId = 0

    /**
     * The next outgoing sequence number to be used.
     */
    private var outSeq = 0

    /**
     * The highest sequence number of an outbound packet that has been sent.
     */
    private var outSeqSent = 0

    /**
     * The sequence number of the highest packet acknowledged by the server.
     */
    private var outSeqAcked = 0

    /**
     * The sequence number we plan on acknowledging receiving with the next Ack. All packets below or equal
     * to inSeq *must* have been received, but not necessarily handled.
     */
    private var inSeq = 0

    /**
     * The highest sequence number we've acknowledged receiving.
     */
    private var inSeqAcked = 0

    /**
     * The highest sequence number we've processed.
     */
    private var inSeqHandled = 0

    private val outPackets: MutableList<UdpPacket> = ArrayList()

    private var inPackets: MutableMap<Int, UdpPacket>? = null

    override var currentEndPoint: InetSocketAddress? = null

    override val localIP: InetAddress?
        get() = sock?.localAddress

    override val protocolTypes: ProtocolTypes
        get() = ProtocolTypes.UDP

    init {
        try {
            sock = DatagramSocket()
        } catch (e: SocketException) {
            throw IllegalStateException("couldn't create datagram socket", e)
        }

        state = AtomicReference(State.DISCONNECTED)
    }

    override fun connect(endPoint: InetSocketAddress, timeout: Int) {
        outPackets.clear()
        inPackets = HashMap()

        currentEndPoint = null
        remoteConnId = 0

        outSeq = 1
        outSeqSent = 0
        outSeqAcked = 0

        inSeq = 0
        inSeqAcked = 0
        inSeqHandled = 0

        logger.debug("connecting to $endPoint")
        netLoop = NetLoop(endPoint)
        netThread = Thread(netLoop, "UdpConnection Thread")
        netThread!!.start()
    }

    override fun disconnect(userInitiated: Boolean) {
        if (netThread == null) {
            return
        }

        // if we think we aren't already disconnected, apply disconnecting unless we read back disconnected
        if (state.get() != State.DISCONNECTED && state.getAndSet(State.DISCONNECTING) == State.DISCONNECTED) {
            state.set(State.DISCONNECTED)
        }

        // only notify if we actually applied the disconnecting state
        if (state.get() == State.DISCONNECTING) {
            // Play nicely and let the server know that we're done. Other party is expected to Ack this,
            // so it needs to be sent sequenced.
            sendSequenced(UdpPacket(EUdpPacketType.Disconnect))
        }

        // Advance this the same way that steam does, when a socket gets reused.
        sourceConnID += 256

        onDisconnected(userInitiated)
    }

    override fun send(data: ByteArray) {
        if (state.get() == State.CONNECTED) {
            sendData(MemoryStream(data))
        }
    }

    /**
     * Sends the data sequenced as a single message, splitting it into multiple parts if necessary.
     *
     * @param ms The data to send.
     */
    private fun sendData(ms: MemoryStream) {
        val packets = arrayOfNulls<UdpPacket>(((ms.length / UdpPacket.MAX_PAYLOAD) + 1).toInt())

        for (i in packets.indices) {
            val index = i.toLong() * UdpPacket.MAX_PAYLOAD
            val length = min(UdpPacket.MAX_PAYLOAD.toDouble(), (ms.length - index).toDouble())
                .toLong()

            packets[i] = UdpPacket(EUdpPacketType.Data, ms, length)
            packets[i]!!.header.msgSize = ms.length.toInt()
        }

        sendSequenced(packets)
    }

    /**
     * Sends the packet as a sequenced, reliable packet.
     *
     * @param packet The packet.
     */
    private fun sendSequenced(packet: UdpPacket) {
        synchronized(outPackets) {
            packet.header.seqThis = outSeq
            packet.header.msgStartSeq = outSeq
            packet.header.packetsInMsg = 1

            outPackets.add(packet)
            outSeq++
        }
    }

    /**
     * Sends the packets as one sequenced, reliable net message.
     *
     * @param packets The packets that make up the single net message
     */
    private fun sendSequenced(packets: Array<UdpPacket?>) {
        synchronized(outPackets) {
            val msgStart = outSeq
            for (packet in packets) {
                sendSequenced(packet!!)

                // Correct for any assumptions made for the single-packet case.
                packet.header.packetsInMsg = packets.size
                packet.header.msgStartSeq = msgStart
            }
        }
    }

    /**
     * Sends a packet immediately.
     *
     * @param packet The packet.
     */
    private fun sendPacket(packet: UdpPacket) {
        packet.header.sourceConnID = sourceConnID
        packet.header.destConnID = remoteConnId
        inSeqAcked = inSeq
        packet.header.seqAck = inSeqAcked

        logger.debug(
            String.format(
                "Sent -> %s Seq %d Ack %d; %d bytes; Message: %d bytes %d packets",
                packet.header.packetType,
                packet.header.seqThis,
                packet.header.seqAck,
                packet.header.payloadSize,
                packet.header.msgSize,
                packet.header.packetsInMsg
            )
        )

        val data = packet.data

        try {
            sock!!.send(DatagramPacket(data, 0, data.size, currentEndPoint!!.address, currentEndPoint!!.port))
        } catch (e: IOException) {
            logger.debug("Critical socket failure", e)
            state.set(State.DISCONNECTING)
            return
        }

        // If we've been idle but completely acked for more than two seconds, the next sent
        // packet will trip the resend detection. This fixes that.
        if (outSeqSent == outSeqAcked) {
            nextResend = System.currentTimeMillis() + RESEND_DELAY
        }

        // Sending should generally carry on from the packet most recently sent, even if it was a
        // resend (who knows what else was lost).
        if (packet.header.seqThis > 0) {
            outSeqSent = packet.header.seqThis
        }
    }

    /**
     * Sends a datagram Ack, used when an Ack needs to be sent but there is no data response to piggy-back on.
     */
    private fun sendAck() {
        sendPacket(UdpPacket(EUdpPacketType.Datagram))
    }

    /**
     * Sends or resends sequenced messages, if necessary. Also responsible for throttling
     * the rate at which they are sent.
     */
    private fun sendPendingMessages() {
        synchronized(outPackets) {
            if (System.currentTimeMillis() > nextResend && outSeqSent > outSeqAcked) {
                // If we can't clear the send queue during a Disconnect, clear out the pending messages
                if (state.get() == State.DISCONNECTING) {
                    outPackets.clear()
                }

                logger.debug("Sequenced packet resend required")

                // Don't send more than 3 (Steam behavior)
                var i = 0
                while (i < RESEND_COUNT && i < outPackets.size) {
                    sendPacket(outPackets[i])
                    i++
                }

                nextResend = System.currentTimeMillis() + RESEND_DELAY
            } else if (outSeqSent < outSeqAcked + AHEAD_COUNT) {
                // I've never seen Steam send more than 4 packets before it gets an Ack, so this limits the
                // number of sequenced packets that can be sent out at one time.
                var i = outSeqSent - outSeqAcked
                while (i < AHEAD_COUNT && i < outPackets.size) {
                    sendPacket(outPackets[i])
                    i++
                }
            }
        }
    }

    /**
     * Returns the number of message parts in the next message.
     *
     * @return Non-zero number of message parts if a message is ready to be handled, 0 otherwise
     */
    private fun readyMessageParts(): Int {
        // Make sure that the first packet of the next message to handle is present
        val packet = inPackets?.get(inSeqHandled + 1) ?: return 0

        // ...and if relevant, all subparts of the message too
        for (i in 1 until packet.header.packetsInMsg) {
            if (!inPackets!!.containsKey(inSeqHandled + 1 + i)) {
                return 0
            }
        }

        return packet.header.packetsInMsg
    }

    /**
     * Dispatches up to one message to the rest of SteamKit
     *
     * @return True if a message was dispatched, false otherwise
     */
    private fun dispatchMessage(): Boolean {
        val numPackets = readyMessageParts()

        if (numPackets == 0) {
            return false
        }

        val baos = ByteArrayOutputStream()
        for (i in 0 until numPackets) {
            val packet = inPackets!![++inSeqHandled]
            inPackets!!.remove(inSeqHandled)

            try {
                baos.write(packet!!.payload.toByteArray())
            } catch (ignored: IOException) {
            }
        }

        val data = baos.toByteArray()

        logger.debug("Dispatchin message: ${data.size} bytes")

        onNetMsgReceived(NetMsgEventArgs(data, currentEndPoint!!))

        return true
    }

    /**
     * Receives the packet, performs all sanity checks and then passes it along as necessary.
     *
     * @param packet The packet.
     */
    private fun receivePacket(packet: UdpPacket) {
        // Check for a malformed packet
        if (!packet.isValid) {
            return
        }

        if (remoteConnId > 0 && packet.header.sourceConnID != remoteConnId) {
            return
        }

        logger.debug(
            String.format(
                "<- Recv'd %s Seq %d Ack %d; %d bytes; Message: %d bytes %d packets",
                packet.header.packetType,
                packet.header.seqThis,
                packet.header.seqAck,
                packet.header.payloadSize,
                packet.header.msgSize,
                packet.header.packetsInMsg
            )
        )

        // Throw away any duplicate messages we've already received, making sure to
        // re-ack it in case it got lost.
        if (packet.header.packetType == EUdpPacketType.Data && packet.header.seqThis < inSeq) {
            sendAck()
            return
        }

        // When we get a SeqAck, all packets with sequence numbers below that have been safely received by
        // the server; we are now free to remove our copies
        if (outSeqAcked < packet.header.seqAck) {
            outSeqAcked = packet.header.seqAck

            // outSeqSent can be less than this in a very rare case involving resent packets.
            if (outSeqSent < outSeqAcked) {
                outSeqSent = outSeqAcked
            }

            outPackets.removeIf { udpPacket: UdpPacket -> udpPacket.header.seqThis <= outSeqAcked }
            nextResend = System.currentTimeMillis() + RESEND_DELAY
        }

        // inSeq should always be the latest value that we can ack, so advance it as far as is possible.
        if (packet.header.seqThis == inSeq + 1) {
            do {
                inSeq++
            } while (inPackets!!.containsKey(inSeq + 1))
        }

        when (packet.header.packetType) {
            EUdpPacketType.Challenge -> receiveChallenge(packet)
            EUdpPacketType.Accept -> receiveAccept(packet)
            EUdpPacketType.Data -> receiveData(packet)
            EUdpPacketType.Disconnect -> {
                logger.debug("Disconnected by server")
                state.set(State.DISCONNECTED)
                return
            }

            EUdpPacketType.Datagram -> {}
            else -> logger.debug("Received unexpected packet type " + packet.header.packetType)
        }
    }

    /**
     * Receives the challenge and responds with a Connect request
     *
     * @param packet The packet.
     */
    private fun receiveChallenge(packet: UdpPacket) {
        if (!state.compareAndSet(State.CHALLENGE_REQ_SENT, State.CONNECT_SENT)) {
            return
        }
        try {
            val cr = ChallengeData()
            cr.deserialize(packet.payload)

            val cd = ConnectData()
            cd.challengeValue = cr.challengeValue xor ConnectData.CHALLENGE_MASK

            val ms = MemoryStream()
            cd.serialize(ms.asOutputStream())
            ms.seek(0, SeekOrigin.BEGIN)

            sendSequenced(UdpPacket(EUdpPacketType.Connect, ms))

            inSeqHandled = packet.header.seqThis
        } catch (e: IOException) {
            logger.debug(e)
        }
    }

    private fun receiveAccept(packet: UdpPacket) {
        if (!state.compareAndSet(State.CONNECT_SENT, State.CONNECTED)) {
            return
        }

        logger.debug("Connection established")
        remoteConnId = packet.header.sourceConnID
        inSeqHandled = packet.header.seqThis

        onConnected()
    }

    private fun receiveData(packet: UdpPacket) {
        // Data packets are unexpected if a valid connection has not been established
        if (state.get() != State.CONNECTED && state.get() != State.DISCONNECTING) {
            return
        }

        // If we receive a packet that we've already processed (e.g. it got resent due to a lost ack)
        // or that is already waiting to be processed, do nothing.
        if (packet.header.seqThis <= inSeqHandled || inPackets!!.containsKey(packet.header.seqThis)) {
            return
        }

        inPackets!![packet.header.seqThis] = packet

        @Suppress("ControlFlowWithEmptyBody")
        while (dispatchMessage()) {
        }
    }

    /**
     * Processes incoming packets, maintains connection consistency, and oversees outgoing packets.
     */
    private inner class NetLoop(endPoint: InetSocketAddress) : Runnable {
        init {
            currentEndPoint = endPoint
        }

        override fun run() {
            // Variables that will be used deeper in the function; locating them here avoids recreating
            // them since they don't need to be.
            var userRequestDisconnect = false
            val buf = ByteArray(2048)
            val packet = DatagramPacket(buf, buf.size)
            var received = false

            try {
                sock!!.soTimeout = 150
            } catch (e: SocketException) {
                logger.debug(e)
            }

            if (currentEndPoint != null) {
                timeout = System.currentTimeMillis() + TIMEOUT_DELAY
                nextResend = System.currentTimeMillis() + RESEND_DELAY

                if (!state.compareAndSet(State.DISCONNECTED, State.CHALLENGE_REQ_SENT)) {
                    state.set(State.DISCONNECTING)
                    userRequestDisconnect = true
                } else {
                    // Begin by sending off the challenge request
                    sendPacket(UdpPacket(EUdpPacketType.ChallengeReq))
                }
            }

            while (state.get() != State.DISCONNECTED) {
                try {
                    try {
                        sock!!.receive(packet)
                        received = true
                    } catch (e: SocketTimeoutException) {
                        if (System.currentTimeMillis() > timeout) {
                            logger.debug("Connection timed out", e)
                            state.set(State.DISCONNECTED)

                            break
                        }
                    }

                    // By using a 10ms wait, we allow for multiple packets sent at the time to all be processed before moving on
                    // to processing output and therefore Acks (the more we process at the same time, the fewer acks we have to send)
                    sock!!.soTimeout = 10
                    while (received) {
                        // Ignore packets that aren't sent by the server we're connected to.
                        if (packet.address != currentEndPoint!!.address && packet.port != packet.port) {
                            continue
                        }

                        timeout = System.currentTimeMillis() + TIMEOUT_DELAY

                        val ms = MemoryStream(packet.data)
                        val udpPacket = UdpPacket(ms)

                        receivePacket(udpPacket)
                        try {
                            sock!!.receive(packet)
                            received = true
                        } catch (e: SocketTimeoutException) {
                            received = false
                        }
                    }
                } catch (e: IOException) {
                    logger.debug("Exception while reading packer", e)
                    state.set(State.DISCONNECTED)
                    break
                }

                // Send or resend any sequenced packets; a call to ReceivePacket can set our state to disconnected
                // so don't send anything we have queued in that case
                if (state.get() != State.DISCONNECTED) {
                    sendPendingMessages()
                }

                // If we received data but had no data to send back, we need to manually Ack (usually tags along with
                // outgoing data); also acks disconnections
                if (inSeq != inSeqAcked) {
                    sendAck()
                }

                // If a graceful shutdown has been requested, nothing in the outgoing queue is discarded.
                // Once it's empty, we exit, since the last packet was our disconnect notification.
                if (state.get() == State.DISCONNECTING && outPackets.isEmpty()) {
                    logger.debug("Graceful disconnect completed")
                    state.set(State.DISCONNECTED)
                    userRequestDisconnect = true
                    break
                }
            }

            sock!!.close()

            logger.debug("Calling onDisconnected")
            onDisconnected(userRequestDisconnect)
        }
    }

    private enum class State {
        DISCONNECTED,
        CHALLENGE_REQ_SENT,
        CONNECT_SENT,
        CONNECTED,
        DISCONNECTING,
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(UdpConnection::class.java)

        private var sourceConnID = 512

        /**
         * Milliseconds to wait before sending packets.
         */
        private const val RESEND_DELAY = 3000L

        /**
         * Milliseconds to wait before considering the connection dead.
         */
        private const val TIMEOUT_DELAY = 60000L

        /**
         * Maximum number of packets to resend when RESEND_DELAY is exceeded.
         */
        private const val RESEND_COUNT = 3

        /**
         * Maximum number of packets that we can be waiting on at a time.
         */
        private const val AHEAD_COUNT = 3
    }
}
