package `in`.dragonbra.javasteam.base.gc

import `in`.dragonbra.javasteam.base.AbstractMsgBase
import `in`.dragonbra.javasteam.base.MsgBase
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.lang.reflect.InvocationTargetException

/**
 * This is the abstract base class for all available game coordinator messages.
 * It's used to maintain packet payloads and provide a header for all gc messages.
 *
 * @param HdrType The header type for this gc message.
 *
 * @constructor Initializes a new instance of the [GCMsgBase] class.
 * @param clazz          the type of the header
 * @param payloadReserve The number of bytes to initialize the payload capacity to.
 */
abstract class GCMsgBase<HdrType : IGCSerializableHeader> @JvmOverloads constructor(
    clazz: Class<HdrType>,
    payloadReserve: Int = 0,
) : AbstractMsgBase(payloadReserve), IClientGCMsg {

    /**
     * @return the header for this message type.
     */
    lateinit var header: HdrType

    init {
        try {
            header = clazz.getDeclaredConstructor().newInstance()
        } catch (e: NoSuchMethodException) {
            logger.debug(e)
        } catch (e: InstantiationException) {
            logger.debug(e)
        } catch (e: IllegalAccessException) {
            logger.debug(e)
        } catch (e: InvocationTargetException) {
            logger.debug(e)
        }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(MsgBase::class.java)
    }
}
