package `in`.dragonbra.javasteam.util.log

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

/**
 * @author lngtr
 * @since 2018-03-02
 */
class DefaultLogListener : LogListener {

    override fun onLog(clazz: Class<*>, message: String?, throwable: Throwable?) {
        var threadName = Thread.currentThread().name
        threadName = threadName.substring(0, min(10.0, threadName.length.toDouble()).toInt())

        if (message == null) {
            System.out.printf("%s [%10s] %s%n", FORMAT.format(Date()), threadName, clazz.name)
        } else {
            System.out.printf("%s [%10s] %s - %s%n", FORMAT.format(Date()), threadName, clazz.name, message)
        }

        throwable?.printStackTrace()
    }

    override fun onError(clazz: Class<*>, message: String?, throwable: Throwable?) {
        var threadName = Thread.currentThread().name
        threadName = threadName.substring(0, min(10.0, threadName.length.toDouble()).toInt())

        if (message == null) {
            System.err.printf("%s [%10s] %s%n", FORMAT.format(Date()), threadName, clazz.name)
        } else {
            System.err.printf("%s [%10s] %s - %s%n", FORMAT.format(Date()), threadName, clazz.name, message)
        }

        throwable?.printStackTrace()
    }

    companion object {
        private val FORMAT = SimpleDateFormat("HH:mm:ss.SSS")
    }
}
