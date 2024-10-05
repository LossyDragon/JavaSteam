package `in`.dragonbra.javasteam.util

import org.apache.commons.lang3.SystemUtils
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * @author lngtr
 * @since 2018-02-24
 */
object HardwareUtils {

    // Everything taken from here
    // https://stackoverflow.com/questions/1986732/how-to-get-a-unique-computer-identifier-in-java-like-disk-id-or-motherboard-id
    private var serialNumber: String? = null

    @JvmStatic
    fun getMachineID(): ByteArray? {
        // the aug 25th 2015 CM update made well-formed machine MessageObjects required for logon
        // this was flipped off shortly after the update rolled out, likely due to linux steamclients running on distros without a way to build a machineid
        // so while a valid MO isn't currently (as of aug 25th) required, they could be in the future and we'll abide by The Valve Law now

        if (serialNumber != null) {
            return serialNumber!!.toByteArray()
        }

        serialNumber = when {
            SystemUtils.IS_OS_WINDOWS -> getSerialNumberWin()
            SystemUtils.IS_OS_MAC -> getSerialNumberMac()
            SystemUtils.IS_OS_LINUX -> getSerialNumberUnix()
            else -> "JavaSteam-SerialNumber"
        }

        return serialNumber!!.toByteArray()
    }

    private fun getSerialNumberWin(): String? =
        executeCommand(arrayOf("wmic", "bios", "get", "serialnumber")) { reader ->
            reader.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "SerialNumber" }
                .firstOrNull()
        }

    private fun getSerialNumberMac(): String? {
        val marker = "Serial Number"
        return executeCommand(arrayOf("/usr/sbin/system_profiler", "SPHardwareDataType")) { reader ->
            reader.lineSequence().firstOrNull { it.contains(marker) }
                ?.split(":")
                ?.getOrNull(1)?.trim()
        }
    }

    private fun getSerialNumberUnix(): String? = readDmidecode() ?: readLshal()

    private fun readDmidecode(): String? {
        val marker = "Serial Number:"
        return executeCommand(arrayOf("dmidecode", "-t", "system")) { reader ->
            reader.lineSequence()
                .firstOrNull { it.contains(marker) }
                ?.split(marker)
                ?.getOrNull(1)
                ?.trim()
        }
    }

    // is lshal still a thing?
    private fun readLshal(): String? {
        val marker = "system.hardware.serial ="
        return executeCommand(arrayOf("lshal")) { reader ->
            reader.lineSequence()
                .firstOrNull { it.contains(marker) }
                ?.split(marker)
                ?.getOrNull(1)
                ?.replace(Regex("\\(string\\)|'"), "")
                ?.trim()
        }
    }

    private fun executeCommand(command: Array<String>, processOutput: (BufferedReader) -> String?): String? {
        val process: Process = try {
            Runtime.getRuntime().exec(command)
        } catch (_: IOException) {
            return null
        }

        process.outputStream.close()

        val inStream = InputStreamReader(process.inputStream)
        return BufferedReader(inStream).use { processOutput(it) }
    }
}
