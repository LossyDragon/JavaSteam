package `in`.dragonbra.javasteam.base

import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.generated.MsgHdr
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.jvm.Throws

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
constructor(
    private val eMsg: EMsg,
    data: ByteArray,
) : IPacketMsg {

    private val msgHdr = MsgHdr()

    private val payload: ByteArray = data

    override val isProto: Boolean
        get() = false

    override val msgType: EMsg
        get() = eMsg

    override val targetJobID: Long
        get() = msgHdr.targetJobID

    override val sourceJobID: Long
        get() = msgHdr.sourceJobID

    override val data: ByteArray
        get() = payload

    init {
        ByteArrayInputStream(data).use(msgHdr::deserialize)
    }
}
