package `in`.dragonbra.javasteam.base

import com.google.protobuf.AbstractMessage
import com.google.protobuf.GeneratedMessage
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.generated.MsgHdrProtoBuf
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import `in`.dragonbra.javasteam.util.stream.BinaryReader
import `in`.dragonbra.javasteam.util.stream.SeekOrigin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.function.Supplier

/**
 * Represents a protobuf backed client message.
 * @constructor Initializes a new instance of the [ClientMsgProtobuf] class.
 * This is a client send constructor.
 * @param newBody Factory for the body builder, typically a `newBuilder` method reference such as `CMsgClientLogOff::newBuilder`.
 * @param eMsg The network message type this client message represents.
 * @param payloadReserve The number of bytes to initialize the payload capacity to.
 */
class ClientMsgProtobuf<BodyType : GeneratedMessage.Builder<BodyType>> @JvmOverloads constructor(
    private val newBody: Supplier<BodyType>,
    eMsg: EMsg,
    payloadReserve: Int = 64,
) : AClientMsgProtobuf(payloadReserve) {

    companion object {
        private val logger: Logger = LogManager.getLogger(ClientMsgProtobuf::class.java)

        /**
         * Adapts a message class to a builder factory by reflecting on its static `newBuilder` method.
         * Only used by the deprecated [Class] based constructors.
         */
        @JvmStatic
        fun <BodyType : GeneratedMessage.Builder<BodyType>> builderOf(clazz: Class<out AbstractMessage>): Supplier<BodyType> {
            val newBuilder = clazz.getMethod("newBuilder")
            return Supplier {
                @Suppress("UNCHECKED_CAST")
                newBuilder.invoke(null) as BodyType
            }
        }
    }

    /**
     * Gets the body structure of this message.
     */
    var body: BodyType = newBody.get()
        private set

    /**
     * Initializes a new instance of the [ClientMsgProtobuf] class.
     * This is a client receive constructor.
     *
     * @param newBody Factory for the body builder, typically a `newBuilder` method reference.
     * @param msg     The packet message to deserialize.
     */
    constructor(newBody: Supplier<BodyType>, msg: IPacketMsg) : this(newBody, msg, 64)

    /**
     * Initializes a new instance of the [ClientMsgProtobuf] class.
     * This is a client receive constructor.
     *
     * @param newBody        Factory for the body builder, typically a `newBuilder` method reference.
     * @param msg            The packet message to deserialize.
     * @param payloadReserve The number of bytes to initialize the payload capacity to.
     */
    constructor(newBody: Supplier<BodyType>, msg: IPacketMsg, payloadReserve: Int) : this(
        newBody,
        msg.msgType,
        payloadReserve
    ) {
        if (!msg.isProto) {
            logger.debug("ClientMsgProtobuf<" + body.javaClass.simpleName + "> used for non-proto message!")
        }
        deserialize(msg.data)
    }

    /**
     * Initializes a new instance of the [ClientMsgProtobuf] class.
     * This is a reply constructor.
     *
     * @param newBody        Factory for the body builder, typically a `newBuilder` method reference.
     * @param eMsg           The network message type this client message represents.
     * @param msg            The message that this instance is a reply for.
     * @param payloadReserve The number of bytes to initialize the payload capacity to.
     */
    @JvmOverloads
    constructor(
        newBody: Supplier<BodyType>,
        eMsg: EMsg,
        msg: MsgBase<MsgHdrProtoBuf>,
        payloadReserve: Int = 64,
    ) : this(newBody, eMsg, payloadReserve) {
        // our target is where the message came from
        header.proto.setJobidTarget(msg.header.proto.jobidSource)
    }

    init {
        header.setEMsg(eMsg)
    }

    /**
     * Serializes this client message instance to a byte array.
     * @return The data representing a client message.
     */
    override fun serialize(): ByteArray {
        try {
            ByteArrayOutputStream(0).use { baos ->
                header.serialize(baos)
                baos.write(body.build().toByteArray())
                baos.write(payload.toByteArray())
                return baos.toByteArray()
            }
        } catch (e: IOException) {
            logger.debug(e)
        }

        return ByteArray(0)
    }

    /**
     * Initializes this client message by deserializing the specified data.
     * @param data The data representing a client message.
     */
    override fun deserialize(data: ByteArray) {
        val ms = BinaryReader(ByteArrayInputStream(data))

        try {
            header.deserialize(ms)
            body = newBody.get().mergeFrom(ms)
            payload.write(data, ms.position, ms.available())
            payload.seek(0, SeekOrigin.BEGIN)
        } catch (e: IOException) {
            logger.debug(e)
        }

        // TODO can be MemoryStream
        try {
            ms.close()
        } catch (e: IOException) {
            logger.error(e)
        }
    }
}
