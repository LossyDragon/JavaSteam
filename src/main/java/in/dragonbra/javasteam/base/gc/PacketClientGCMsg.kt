package `in`.dragonbra.javasteam.base.gc

import `in`.dragonbra.javasteam.generated.MsgGCHdr
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.util.stream.MemoryStream

/**
 * Represents a packet message with extended header information.
 *
 * @constructor Initializes a new instance of the [PacketClientGCMsg] class.
 * @param eMsg The network message type for this packet message.
 * @param data The data.
 */
class PacketClientGCMsg(eMsg: Int, data: ByteArray) : IPacketGCMsg {

    private val payload: ByteArray = data

    override val isProto: Boolean
        get() = false

    override val msgType: Int = eMsg

    override val targetJobID: JobID

    override val sourceJobID: JobID

    override val data: ByteArray
        get() = payload

    init {
        // we need to pull out the job ids, so we deserialize the protobuf header
        val gcHdr = MsgGCHdr()
        val ms = MemoryStream(data)
        gcHdr.deserialize(ms)

        targetJobID = JobID(gcHdr.targetJobID)
        sourceJobID = JobID(gcHdr.sourceJobID)
    }
}
