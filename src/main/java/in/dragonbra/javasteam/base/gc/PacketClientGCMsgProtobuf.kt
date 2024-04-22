package `in`.dragonbra.javasteam.base.gc

import `in`.dragonbra.javasteam.generated.MsgGCHdrProtoBuf
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.util.stream.MemoryStream

/**
 * Represents a protobuf backed packet message.
 *
 * @constructor Initializes a new instance of the [PacketClientGCMsgProtobuf] class.
 * @param eMsg The network message type for this packet message.
 * @param data The data.
 */
class PacketClientGCMsgProtobuf(eMsg: Int, data: ByteArray) : IPacketGCMsg {

    private val payload: ByteArray = data

    override val isProto: Boolean
        get() = true

    override val msgType: Int = eMsg

    override val targetJobID: JobID

    override val sourceJobID: JobID

    override val data: ByteArray
        get() = payload

    init {
        // we need to pull out the job ids, so we deserialize the protobuf header
        val protobufHeader = MsgGCHdrProtoBuf()
        val ms = MemoryStream(data)
        protobufHeader.deserialize(ms)

        targetJobID = JobID(protobufHeader.proto.jobidTarget)
        sourceJobID = JobID(protobufHeader.proto.jobidSource)
    }
}
