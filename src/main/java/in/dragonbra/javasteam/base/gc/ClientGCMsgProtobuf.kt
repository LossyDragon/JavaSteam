package `in`.dragonbra.javasteam.base.gc

import com.google.protobuf.AbstractMessage
import com.google.protobuf.GeneratedMessage
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
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
 * @param BodyType The body type of this message.
 */
@Suppress("unused")
class ClientGCMsgProtobuf<BodyType : GeneratedMessage.Builder<BodyType>> : GCMsgBase<MsgGCHdrProtoBuf> {

    private var clazz: Class<out AbstractMessage>

    /**
     * Shorthand accessor for the protobuf header.
     * @return the protobuf header
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val protoHeader: SteammessagesBase.CMsgProtoBufHeader.Builder
        get() = header.proto

    /**
     * Gets the body structure of this message
     * @return the body structure
     */
    lateinit var body: BodyType
        private set

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

    /**
     * Initializes a new instance of the [ClientGCMsgProtobuf] class.
     * This is a client send constructor.
     * @param clazz          the type of the body
     * @param eMsg           The network message type this client message represents.
     * @param payloadReserve The number of bytes to initialize the payload capacity to.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmOverloads
    constructor(
        clazz: Class<out AbstractMessage>,
        eMsg: Int,
        payloadReserve: Int = 64,
    ) : super(MsgGCHdrProtoBuf::class.java, payloadReserve) {
        this.clazz = clazz

        try {
            val m = this.clazz.getMethod("newBuilder")
            body = m.invoke(null) as BodyType
        } catch (e: IllegalAccessException) {
            logger.debug(e)
        } catch (e: NoSuchMethodException) {
            logger.debug(e)
        } catch (e: InvocationTargetException) {
            logger.debug(e)
        }

        header.setEMsg(eMsg)
    }

    /**
     * Initializes a new instance of the [ClientMsgProtobuf] class.
     * This is a client send constructor.
     * @param clazz the type of the body
     * @param msg   The network message type this client message represents.
     */
    constructor(clazz: Class<out AbstractMessage>, msg: IPacketGCMsg) : this(clazz, msg.msgType) {
        if (!msg.isProto) {
            logger.debug("ClientMsgProtobuf<${clazz.simpleName}> used for non-proto message!")
        }
        deserialize(msg.data)
    }

    /**
     * Initializes a new instance of the [ClientGCMsgProtobuf] class.
     * This is a reply constructor.
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

    override fun serialize(): ByteArray {
        val baos = ByteArrayOutputStream()

        try {
            header.serialize(baos)
            body.build().writeTo(baos)
            baos.write(payload.toByteArray())
        } catch (ignored: IOException) {
        }

        return baos.toByteArray()
    }

    @Suppress("UNCHECKED_CAST")
    override fun deserialize(data: ByteArray) {
        val ms = BinaryReader(ByteArrayInputStream(data))

        try {
            header.deserialize(ms)
            val m = clazz.getMethod("newBuilder")
            body = m.invoke(null) as BodyType
            body.mergeFrom(ms)
            payload.write(data, ms.position, ms.available())
            payload.seek(0, SeekOrigin.BEGIN)
        } catch (e: IOException) {
            logger.debug(e)
        } catch (e: IllegalAccessException) {
            logger.debug(e)
        } catch (e: NoSuchMethodException) {
            logger.debug(e)
        } catch (e: InvocationTargetException) {
            logger.debug(e)
        }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(ClientGCMsgProtobuf::class.java)
    }
}
