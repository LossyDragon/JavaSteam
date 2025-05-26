package `in`.dragonbra.javasteam.base

import com.google.protobuf.AbstractMessage
import com.google.protobuf.GeneratedMessage
import `in`.dragonbra.javasteam.generated.MsgGCHdrProtoBuf
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesBase
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import `in`.dragonbra.javasteam.util.stream.BinaryReader
import `in`.dragonbra.javasteam.util.stream.SeekOrigin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException

/**
 * Represents a protobuf backed game coordinator message.
 *
 * @constructor Initializes a new instance of the [ClientGCMsgProtobuf] class.
 *  This is a client send constructor.
 * @param [BodyType] The body type of this message.
 * @param clazz          the type of the body
 * @param eMsg           The network message type this client message represents.
 * @param payloadReserve The number of bytes to initialize the payload capacity to.
 */
@Suppress("unused", "UNCHECKED_CAST")
class ClientGCMsgProtobuf<BodyType : GeneratedMessage.Builder<BodyType>> @JvmOverloads constructor(
    private val clazz: Class<out AbstractMessage>,
    eMsg: Int,
    payloadReserve: Int = 64,
) : GCMsgBase<MsgGCHdrProtoBuf>(MsgGCHdrProtoBuf::class.java, payloadReserve) {

    companion object {
        private val logger: Logger = LogManager.getLogger(ClientGCMsgProtobuf::class.java)
    }

    /**
     * @return the body structure of this message
     */
    lateinit var body: BodyType

    /**
     * Shorthand accessor for the protobuf header.
     * @return the protobuf header
     */
    val protoHeader: SteammessagesBase.CMsgProtoBufHeader.Builder
        get() = header.proto

    /**
     * Initializes a new instance of the [ClientMsgProtobuf] class.
     * This is a client send constructor.
     *
     * @param clazz the type of the body
     * @param msg   The network message type this client message represents.
     */
    constructor(clazz: Class<out AbstractMessage>, msg: IPacketGCMsg) : this(clazz, msg.msgType) {
        if (!msg.isProto) {
            logger.error("ClientMsgProtobuf<${clazz.getSimpleName()}> used for non-proto message!")
        }
        deserialize(msg.data)
    }

    /**
     * Initializes a new instance of the [ClientGCMsgProtobuf] class.
     * This is a reply constructor.
     *
     * @param clazz          the type of the body
     * @param eMsg           The network message type this client message represents.
     * @param msg            The message that this instance is a reply for.
     * @param payloadReserve The number of bytes to initialize the payload capacity to.
     */
    @JvmOverloads
    constructor(
        clazz: Class<out AbstractMessage>,
        eMsg: Int,
        msg: GCMsgBase<MsgGCHdrProtoBuf>,
        payloadReserve: Int = 64,
    ) : this(clazz, eMsg, payloadReserve) {

        // our target is where the message came from
        header.proto.setJobidTarget(msg.header.proto.jobidSource)
    }

    init {
        try {
            val m = clazz.getMethod("newBuilder")
            body = m.invoke(null) as BodyType
        } catch (e: IllegalAccessException) {
            logger.error(e)
        } catch (e: NoSuchMethodException) {
            logger.error(e)
        } catch (e: InvocationTargetException) {
            logger.error(e)
        }

        header.setEMsg(eMsg)
    }

    override val isProto: Boolean
        get() = true

    override val msgType: Int
        get() = header.msg

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

    override fun serialize(): ByteArray {
        try {
            ByteArrayOutputStream().use { baos ->
                header.serialize(baos)
                body.build().writeTo(baos)
                baos.write(payload.toByteArray())
                return baos.toByteArray()
            }
        } catch (ignored: IOException) {
        }

        return ByteArray(0)
    }

    override fun deserialize(data: ByteArray) {
        try {
            BinaryReader(ByteArrayInputStream(data)).use { ms ->
                header.deserialize(ms)
                val m = clazz.getMethod("newBuilder")
                body = m.invoke(null) as BodyType
                body.mergeFrom(ms)
                payload.write(data, ms.position, ms.available())
                payload.seek(0, SeekOrigin.BEGIN)
            }
        } catch (e: IOException) {
            logger.error(e)
        } catch (e: IllegalAccessException) {
            logger.error(e)
        } catch (e: NoSuchMethodException) {
            logger.error(e)
        } catch (e: InvocationTargetException) {
            logger.error(e)
        }
    }
}
