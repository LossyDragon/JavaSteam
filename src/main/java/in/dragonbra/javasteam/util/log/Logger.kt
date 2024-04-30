package `in`.dragonbra.javasteam.util.log

/**
 * @author lngtr
 * @since 2018-03-02
 */
class Logger internal constructor(private val clazz: Class<*>) {

    fun debug(throwable: Throwable) {
        debug(null, throwable)
    }

    @JvmOverloads
    fun debug(message: String?, throwable: Throwable? = null) {
        LogManager.LOG_LISTENERS.forEach { listener ->
            listener.onLog(clazz, message, throwable)
        }
    }

    fun error(throwable: Throwable) {
        error(null, throwable)
    }

    @JvmOverloads
    fun error(message: String?, throwable: Throwable? = null) {
        LogManager.LOG_LISTENERS.forEach { listener ->
            listener.onError(clazz, message, throwable)
        }
    }
}
