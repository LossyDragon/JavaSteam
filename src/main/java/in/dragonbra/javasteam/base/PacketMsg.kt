package `in`.dragonbra.javasteam.base

import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.generated.MsgHdr
import `in`.dragonbra.javasteam.util.stream.MemoryStream
import java.io.IOException

/**
 * Represents a packet message with basic header information.
 *
 * @constructor Initializes a new instance of the[PacketMsg] class.
 * @param eMsg The network message type for this packet message.
 * @param data The data.
 * @throws IOException exception while deserializing the data
 */
class PacketMsg
@Throws(IOException::class)
constructor(eMsg: EMsg, data: ByteArray) : IPacketMsg {

    /**
     * Gets the header for this packet message.
     * @return The header.
     */
    internal val header: MsgHdr = MsgHdr()

    /**
     * Gets the offset in payload to the body after the header.
     * @return The offset in payload after the header.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    internal val bodyOffset: Long

    private val payload: ByteArray = data

    override val isProto: Boolean
        get() = false

    override val msgType: EMsg = eMsg

    override val targetJobID: Long
        get() = header.targetJobID

    override val sourceJobID: Long
        get() = header.sourceJobID

    override val data: ByteArray = payload

    init {
        // deserialize the header to get our hands on the job ids
        val ms = MemoryStream(data)
        header.deserialize(ms)
        bodyOffset = ms.position
    }
}
