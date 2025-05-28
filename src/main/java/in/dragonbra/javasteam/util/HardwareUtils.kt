package `in`.dragonbra.javasteam.util

import org.apache.commons.lang3.SystemUtils
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * @author lngtr
 * @since 2018-02-24
 */
object HardwareUtils {

    @Suppress("ktlint:standard:property-naming")
    private val SERIAL_NUMBER: String by lazy {
        when {
            SystemUtils.IS_OS_WINDOWS -> getSerialNumberWin()
            SystemUtils.IS_OS_MAC -> getSerialNumberMac()
            SystemUtils.IS_OS_LINUX -> getSerialNumberUnix()
            else -> null
        } ?: "JavaSteam-SerialNumber"
    }

    @Suppress("ktlint:standard:property-naming")
    private val MACHINE_NAME: String by lazy {
        val name = if (SystemUtils.IS_OS_ANDROID) {
            getAndroidDeviceName()
        } else {
            getDeviceName()
        }

        if (name.isNullOrBlank()) "Unknown" else name
    }

    @JvmStatic
    val machineID: ByteArray
        get() = SERIAL_NUMBER.toByteArray()

    @JvmOverloads
    @JvmStatic
    fun getMachineName(addTag: Boolean = false): String {
        val name = MACHINE_NAME
        return if (addTag || name.contains("Unknown")) {
            "$name (JavaSteam)"
        } else {
            name
        }
    }

    private fun getSerialNumberWin(): String? =
        executeCommand(arrayOf("wmic", "bios", "get", "serialnumber")) { reader ->
            reader.useLines { lines ->
                lines.windowed(2, 1)
                    .firstOrNull { it[0].contains("SerialNumber", ignoreCase = true) }
                    ?.get(1)?.trim()
            }
        }

    private fun getSerialNumberMac(): String? =
        executeCommand(arrayOf("/usr/sbin/system_profiler", "SPHardwareDataType")) { reader ->
            reader.useLines { lines ->
                lines.firstOrNull { it.contains("Serial Number") }
                    ?.substringAfter(":")
                    ?.trim()
            }
        }

    private fun getSerialNumberUnix(): String? =
        readDmidecode() ?: readLshal()

    private fun readDmidecode(): String? =
        executeCommand("dmidecode -t system") { reader ->
            reader.useLines { lines ->
                lines.firstOrNull { it.contains("Serial Number:") }
                    ?.substringAfter("Serial Number:")
                    ?.trim()
            }
        }

    private fun readLshal(): String? =
        executeCommand("lshal") { reader ->
            reader.useLines { lines ->
                lines.firstOrNull { it.contains("system.hardware.serial =") }
                    ?.substringAfter("system.hardware.serial =")
                    ?.replace(Regex("\\(string\\)|'"), "")
                    ?.trim()
            }
        }

    private inline fun <T> executeCommand(command: Array<String>, processor: (BufferedReader) -> T?): T? =
        try {
            val process = Runtime.getRuntime().exec(command)
            process.outputStream.close() // Close unused output stream

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                processor(reader)
            }
        } catch (_: IOException) {
            null
        }

    private inline fun <T> executeCommand(command: String, processor: (BufferedReader) -> T?): T? =
        executeCommand(command.split(" ").toTypedArray(), processor)

    private fun getDeviceName(): String? =
        SystemUtils.getHostName()
            .takeIf { !it.isNullOrBlank() }
            ?: try {
                InetAddress.getLocalHost().hostName
            } catch (_: UnknownHostException) {
                null
            }

    private fun getAndroidDeviceName(): String {
        val manufacturer = getAndroidSystemProperty("ro.product.manufacturer")
        val model = getAndroidSystemProperty("ro.product.model")

        return when {
            manufacturer == null || model == null -> "Android Device"
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }

    private fun getAndroidSystemProperty(key: String): String? =
        try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java)
            get.invoke(null, key) as? String
        } catch (_: Exception) {
            null
        }
}
