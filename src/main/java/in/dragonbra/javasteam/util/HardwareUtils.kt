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

    // Everything referenced from here
    // https://stackoverflow.com/questions/1986732/how-to-get-a-unique-computer-identifier-in-java-like-disk-id-or-motherboard-id
    private var serialNumber: String? = null

    @JvmStatic
    val machineID: ByteArray
        get() {
            // the aug 25th 2015 CM update made well-formed machine MessageObjects required for logon
            // this was flipped off shortly after the update rolled out, likely due to linux steamclients running on distros without a way to build a machineid
            // so while a valid MO isn't currently (as of aug 25th) required, they could be in the future and we'll abide by The Valve Law now

            if (serialNumber != null) {
                return serialNumber!!.toByteArray()
            }

            serialNumber = when {
                SystemUtils.IS_OS_WINDOWS -> getSerialNumber("wmic bios get serialnumber", "SerialNumber")
                SystemUtils.IS_OS_MAC -> getSerialNumber("/usr/sbin/system_profiler", "SPHardwareDataType")
                SystemUtils.IS_OS_LINUX -> getSerialNumberLinux()
                else -> "Unknown-JavaSteam"
            }

            return serialNumber!!.toByteArray()
        }

    private fun getSerialNumber(command: String, marker: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(command)
            process.outputStream.close()
            BufferedReader(InputStreamReader(process.inputStream)).useLines { line ->
                line.mapNotNull {
                    if (it.contains(marker)) {
                        it.substringAfter(marker).trim()
                    } else {
                        null
                    }
                }.firstOrNull()
            }
        } catch (e: IOException) {
            // Unable to get SN
            null
        }
    }

    // Could streamline this?
    private fun getSerialNumberLinux(): String? {
        return try {
            var process = Runtime.getRuntime().exec("dmidecode -t system")
            process.outputStream.close()

            var sn = BufferedReader(InputStreamReader(process.inputStream))
                .lineSequence()
                .mapNotNull {
                    val marker = "Serial Number:"
                    if (it.contains(marker)) {
                        it.substringAfter(marker).replace("(string)|(')".toRegex(), "").trim()
                    } else {
                        null
                    }
                }.firstOrNull()

            if (sn == null) {
                process = Runtime.getRuntime().exec("lshal")
                process.outputStream.close()

                sn = BufferedReader(InputStreamReader(process.inputStream))
                    .lineSequence()
                    .mapNotNull {
                        val marker = "system.hardware.serial ="
                        if (it.contains(marker)) {
                            it.substringAfter(marker).replace("(string)|(')".toRegex(), "").trim()
                        } else {
                            null
                        }
                    }.firstOrNull()
            }

            sn
        } catch (e: IOException) {
            // Unable to get SN
            null
        }
    }
}
