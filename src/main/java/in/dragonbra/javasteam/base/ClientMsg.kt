package `in`.dragonbra.javasteam.base

import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.generated.ExtendedClientMsgHdr
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import `in`.dragonbra.javasteam.util.stream.MemoryStream
import `in`.dragonbra.javasteam.util.stream.SeekOrigin
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException

/**
 * Represents a struct backed client message.
 *
 * @param BodyType The body type of this message.
 */
@Suppress("unused")
class ClientMsg<BodyType : ISteamSerializableMessage> : MsgBase<ExtendedClientMsgHdr> {

    /**
     * Gets or Sets the body structure of this message.
     * @return the body structure of this message.
     */
    lateinit var body: BodyType

    override val isProto: Boolean
        get() = false

    override val msgType: EMsg
        get() = header.msg

    override var steamID: SteamID?
        get() = header.steamID
        set(steamID) {
            header.steamID = steamID
        }

    override var sessionID: Int?
        get() = header.sessionID
        set(sessionID) {
            header.sessionID = sessionID!!
        }

    override var targetJobID: JobID
        get() = JobID(header.targetJobID)
        set(jobID) {
            header.targetJobID = jobID.value
        }

    override var sourceJobID: JobID
        get() = JobID(header.sourceJobID)
        set(jobID) {
            header.sourceJobID = jobID.value
        }

    /**
     * Initializes a new instance of the [ClientMsg] class.
     * This is a client send constructor.
     * @param bodyType       body type
     * @param payloadReserve The number of bytes to initialize the payload capacity to.
     */
    @JvmOverloads
    constructor(
        bodyType: Class<out BodyType>,
        payloadReserve: Int = 64,
    ) : super(ExtendedClientMsgHdr::class.java, payloadReserve) {
        try {
            body = bodyType.getDeclaredConstructor().newInstance()
        } catch (e: NoSuchMethodException) {
            logger.debug(e)
        } catch (e: InstantiationException) {
            logger.debug(e)
        } catch (e: IllegalAccessException) {
            logger.debug(e)
        } catch (e: InvocationTargetException) {
            logger.debug(e)
        }

        header.setEMsg(body.eMsg)
    }

    /**
     * Initializes a new instance of the [ClientMsg] class.
     * This a reply constructor.
     * @param bodyType       body type
     * @param msg            The message that this instance is a reply for.
     * @param payloadReserve The number of bytes to initialize the payload capacity to.
     */
    @JvmOverloads
    constructor(
        bodyType: Class<out BodyType>,
        msg: MsgBase<ExtendedClientMsgHdr>,
        payloadReserve: Int = 64,
    ) : this(bodyType, payloadReserve) {
        // our target is where the message came from
        header.targetJobID = msg.header.sourceJobID
    }

    /**
     * Initializes a new instance of the [ClientMsg] class.
     * This a receive constructor.
     * @param bodyType body type
     * @param msg      The packet message to build this client message from.
     */
    constructor(
        bodyType: Class<out BodyType>,
        msg: IPacketMsg,
    ) : this(bodyType) {
        if (msg.isProto) {
            logger.debug("ClientMsg<" + bodyType.name + "> used for proto message!")
        }

        deserialize(msg.data)
    }

    override fun serialize(): ByteArray {
        val baos = ByteArrayOutputStream(0)

        try {
            header.serialize(baos)
            body.serialize(baos)
            baos.write(payload.toByteArray())
        } catch (e: IOException) {
            logger.debug(e)
        }

        return baos.toByteArray()
    }

    override fun deserialize(data: ByteArray) {
        val ms = MemoryStream(data)

        try {
            header.deserialize(ms)
            body.deserialize(ms)
        } catch (e: IOException) {
            logger.debug(e)
        }

        payload.write(data, ms.position.toInt(), ms.available())
        payload.seek(0, SeekOrigin.BEGIN)
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(ClientMsg::class.java)
    }
}
