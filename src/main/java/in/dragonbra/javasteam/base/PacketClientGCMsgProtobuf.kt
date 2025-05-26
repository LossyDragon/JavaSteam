package `in`.dragonbra.javasteam.base

import `in`.dragonbra.javasteam.generated.MsgGCHdrProtoBuf
import `in`.dragonbra.javasteam.types.JobID
import java.io.ByteArrayInputStream

/**
 * Represents a protobuf backed packet message.
 *
 * @constructor Initializes a new instance of the [PacketClientGCMsgProtobuf] class.
 * @param eMsg The network message type for this packet message.
 * @param data The data.
 */
class PacketClientGCMsgProtobuf(
    private val eMsg: Int,
    data: ByteArray,
) : IPacketGCMsg {

    private val payload: ByteArray = data

    private val protobufHeader = MsgGCHdrProtoBuf()

    override val isProto: Boolean
        get() = true

    override val msgType: Int
        get() = eMsg

    override val targetJobID: JobID
        get() = JobID(protobufHeader.proto.jobidTarget)

    override val sourceJobID: JobID
        get() = JobID(protobufHeader.proto.jobidSource)

    override val data: ByteArray
        get() = payload

    init {
        // we need to pull out the job ids, so we deserialize the protobuf header
        ByteArrayInputStream(data).use(protobufHeader::deserialize)
    }
}
