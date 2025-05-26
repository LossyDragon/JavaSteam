package `in`.dragonbra.javasteam.base

import `in`.dragonbra.javasteam.generated.MsgGCHdr
import `in`.dragonbra.javasteam.types.JobID
import java.io.ByteArrayInputStream

/**
 * Represents a packet message with extended header information.
 *
 * @constructor Initializes a new instance of the [PacketClientGCMsg] class.
 * @param eMsg The network message type for this packet message.
 * @param data The data.
 */
class PacketClientGCMsg(
    private val eMsg: Int,
    data: ByteArray,
) : IPacketGCMsg {

    private val payload: ByteArray = data

    private val gcHdr = MsgGCHdr()

    override val isProto: Boolean
        get() = false

    override val msgType: Int
        get() = eMsg

    override val targetJobID: JobID
        get() = JobID(gcHdr.targetJobID)

    override val sourceJobID: JobID
        get() = JobID(gcHdr.sourceJobID)

    override val data: ByteArray
        get() = payload

    init {
        // we need to pull out the job ids, so we deserialize the protobuf header
        ByteArrayInputStream(data).use(gcHdr::deserialize)
    }
}
