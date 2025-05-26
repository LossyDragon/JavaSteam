package `in`.dragonbra.javasteam.base

import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.generated.MsgHdrProtoBuf
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.jvm.Throws

/**
 * Represents a protobuf backed packet message.
 *
 * @constructor Initializes a new instance of the [PacketClientMsgProtobuf] class.
 * @param eMsg The network message type for this packet message.
 * @param data The data.
 * @throws IOException exception while deserializing the data
 */
class PacketClientMsgProtobuf
@Throws(IOException::class)
constructor(
    private val eMsg: EMsg,
    data: ByteArray,
) : IPacketMsg {

    private val payload: ByteArray = data

    /**
     * Gets the header for this packet message.
     * @return The header.
     */
    val header: MsgHdrProtoBuf = MsgHdrProtoBuf()

    override val isProto: Boolean
        get() = true

    override val msgType: EMsg
        get() = eMsg

    override val targetJobID: Long
        get() = header.proto.jobidTarget

    override val sourceJobID: Long
        get() = header.proto.jobidSource

    override val data: ByteArray
        get() = payload

    init {
        ByteArrayInputStream(data).use(header::deserialize)
    }
}
