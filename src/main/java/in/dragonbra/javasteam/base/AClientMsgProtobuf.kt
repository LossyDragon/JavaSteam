package `in`.dragonbra.javasteam.base

import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.generated.MsgHdrProtoBuf
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesBase
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Represents a protobuf backed client message. Only contains the header information.
 */
@Suppress("unused")
open class AClientMsgProtobuf : MsgBase<MsgHdrProtoBuf> {

    /**
     * Shorthand accessor for the protobuf header.
     * @return the protobuf header.
     */
    val protoHeader: SteammessagesBase.CMsgProtoBufHeader.Builder
        get() = header.proto

    override val isProto: Boolean
        get() = true

    override val msgType: EMsg
        get() = header.msg

    override var sessionID: Int?
        get() = protoHeader.clientSessionid
        set(sessionID) {
            protoHeader.setClientSessionid(sessionID!!)
        }

    override var steamID: SteamID?
        get() = SteamID(protoHeader.steamid)
        set(steamID) {
            protoHeader.setSteamid(steamID!!.convertToUInt64())
        }

    override var targetJobID: JobID
        get() = JobID(protoHeader.jobidTarget)
        set(jobID) {
            protoHeader.setJobidTarget(jobID.value)
        }

    override var sourceJobID: JobID
        get() = JobID(protoHeader.jobidSource)
        set(jobID) {
            protoHeader.setJobidSource(jobID.value)
        }

    /**
     *
     */
    internal constructor(payloadReserve: Int) : super(MsgHdrProtoBuf::class.java, payloadReserve)

    private constructor() : this(0)

    private constructor(eMsg: EMsg, payloadReserve: Int = 0) : super(MsgHdrProtoBuf::class.java, payloadReserve) {
        // set our emsg
        header.setEMsg(eMsg)
    }

    /**
     * Initializes a new instance of the [AClientMsgProtobuf] class.
     * This is a recieve constructor.
     * @param msg The packet message to build this client message from.
     */
    constructor(msg: IPacketMsg) : this(msg.msgType) {
        if (!msg.isProto) {
            logger.debug("ClientMsgProtobuf used for non-proto message!")
        }

        // TODO: Calling non-final function deserialize in constructor
        deserialize(msg.data)
    }

    override fun serialize(): ByteArray {
        throw UnsupportedOperationException("ClientMsgProtobuf is for reading only. Use ClientMsgProtobuf<T> for serializing messages.")
    }

    override fun deserialize(data: ByteArray) {
        try {
            header.deserialize(ByteArrayInputStream(data))
        } catch (e: IOException) {
            logger.debug(e)
        }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(AClientMsgProtobuf::class.java)
    }
}
