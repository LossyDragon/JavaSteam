package `in`.dragonbra.javasteam.networking.steam3

import `in`.dragonbra.javasteam.enums.EUdpPacketType
import `in`.dragonbra.javasteam.generated.UdpHeader
import `in`.dragonbra.javasteam.util.stream.MemoryStream
import `in`.dragonbra.javasteam.util.stream.SeekOrigin
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * @author lngtr
 * @since 2018-03-01
 */
internal class UdpPacket {

    /**
     * TODO kDoc
     */
    val header: UdpHeader

    /**
     * Serializes the UdpPacket.
     * @return The serialized packet.
     */
    val data: ByteArray
        get() {
            val baos = ByteArrayOutputStream()

            try {
                header.serialize(baos)
                payload.seek(0, SeekOrigin.BEGIN)
                baos.write(payload.toByteArray())
            } catch (ignored: IOException) {
            }

            return baos.toByteArray()
        }

    /**
     * TODO kDoc
     */
    lateinit var payload: MemoryStream
        private set

    /**
     * Gets a value indicating whether this instance is valid.
     * @return **true** if this instance is valid; otherwise, **false**.
     */
    val isValid: Boolean
        get() = header.magic == UdpHeader.MAGIC && header.payloadSize <= MAX_PAYLOAD

    /**
     * Initializes a new instance of the [UdpPacket] class with Header is populated from the MemoryStream
     * @param ms The stream containing the packet, and it's payload data.
     */
    constructor(ms: MemoryStream) {
        header = UdpHeader()

        try {
            header.deserialize(ms)
        } catch (e: IOException) {
            return
        }

        if (header.magic != UdpHeader.MAGIC) {
            return
        }

        setPayload(ms, header.payloadSize.toLong())
    }

    /**
     * Initializes a new instance of the [UdpPacket] class, with no payload.
     * Header must be populated manually.
     * @param type The type.
     */
    constructor(type: EUdpPacketType) {
        header = UdpHeader().apply {
            packetType = type
        }
        payload = MemoryStream()
    }

    /**
     * Initializes a new instance of the [UdpPacket] class, of the specified type containing the specified payload.
     *  Header must be populated manually.
     * @param type    The type.
     * @param payload The payload.
     */
    constructor(type: EUdpPacketType, payload: MemoryStream) : this(type) {
        setPayload(payload)
    }

    /**
     * Initializes a new instance of the [UdpPacket] class,
     *  of the specified type containing the first 'length' bytes of specified payload.
     *  Header must be populated manually.
     * @param type    The type.
     * @param payload The payload.
     * @param length  The length.
     */
    constructor(type: EUdpPacketType, payload: MemoryStream, length: Long) : this(type) {
        setPayload(payload, length)
    }

    /**
     * Sets the payload
     * @param ms The payload to copy.
     */
    fun setPayload(ms: MemoryStream) {
        setPayload(ms, ms.length - ms.position)
    }

    /**
     * TODO kDoc
     */
    fun setPayload(ms: MemoryStream, length: Long) {
        require(length <= MAX_PAYLOAD) { "Payload length exceeds 0x4DC maximum" }

        val buf = ByteArray(length.toInt())
        ms.read(buf, 0, buf.size)

        payload = MemoryStream(buf)
        header.payloadSize = payload.length.toShort()
        header.msgSize = payload.length.toInt()
    }

    companion object {
        const val MAX_PAYLOAD: Int = 0x4DC
    }
}
