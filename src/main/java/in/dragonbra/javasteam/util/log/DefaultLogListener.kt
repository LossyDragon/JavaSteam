package `in`.dragonbra.javasteam.util.log

import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date

/**
 * @author lngtr
 * @since 2018-03-02
 */
class DefaultLogListener : LogListener {

    companion object {
        private val FORMAT = SimpleDateFormat("HH:mm:ss.SSS")
    }

    override fun onLog(clazz: Class<*>, message: String?, throwable: Throwable?) {
        logMessage(System.out, clazz, message)
        throwable?.printStackTrace()
    }

    override fun onError(clazz: Class<*>, message: String?, throwable: Throwable?) {
        logMessage(System.err, clazz, message)
        throwable?.printStackTrace()
    }

    private fun logMessage(output: PrintStream, clazz: Class<*>, message: String?) {
        val threadName = Thread.currentThread().name.take(16)
        val className = clazz.name
        val timestamp = FORMAT.format(Date())

        if (message == null) {
            output.printf("%s [%16s] %s%n", timestamp, threadName, className)
        } else {
            output.printf("%s [%16s] %s - %s%n", timestamp, threadName, className, message)
        }
    }
}
