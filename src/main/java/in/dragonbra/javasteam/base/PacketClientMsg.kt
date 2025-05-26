package `in`.dragonbra.javasteam.base

import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.generated.ExtendedClientMsgHdr
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Represents a packet message with extended header information.
 *
 * @constructor Initializes a new instance of the [PacketClientMsg] class.
 * @param eMsg The network message type for this packet message.
 * @param data The data.
 * @throws IOException exception while deserializing the data
 */
class PacketClientMsg
@Throws(IOException::class)
constructor(
    private val eMsg: EMsg,
    data: ByteArray,
) : IPacketMsg {

    private val extendedHdr = ExtendedClientMsgHdr()

    private val payload = data

    override val isProto: Boolean
        get() = false

    override val msgType: EMsg
        get() = eMsg

    override val targetJobID: Long
        get() = extendedHdr.targetJobID

    override val sourceJobID: Long
        get() = extendedHdr.sourceJobID

    override val data: ByteArray
        get() = payload

    init {
        ByteArrayInputStream(data).use(extendedHdr::deserialize)
    }
}
