package `in`.dragonbra.javasteam.util

import `in`.dragonbra.javasteam.enums.EOSType
import org.apache.commons.lang3.SystemUtils
import java.util.zip.CRC32
import java.util.zip.Checksum

/**
 * @author lngtr
 * @since 2018-02-23
 */
object Utils {

    private val JAVA_RUNTIME = getSystemProperty("java.runtime.name")

    private val WIN_OS_MAP: LinkedHashMap<Boolean, EOSType> = LinkedHashMap()

    private val OSX_OS_MAP: LinkedHashMap<Boolean, EOSType> = LinkedHashMap()

    private val LINUX_OS_MAP: LinkedHashMap<String, EOSType> = LinkedHashMap()

    private val GENERIC_LINUX_OS_MAP: LinkedHashMap<String, EOSType> = LinkedHashMap()

    init {
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_95] = EOSType.Win95
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_98] = EOSType.Win98
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_ME] = EOSType.WinME
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_NT] = EOSType.WinNT
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_2000] = EOSType.Win2000
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_XP] = EOSType.WinXP
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_VISTA] = EOSType.WinVista
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_7] = EOSType.Windows7
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_8] = EOSType.Windows8
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_10] = EOSType.Windows10
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_11] = EOSType.Win11
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_2003] = EOSType.Win2003
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_2008] = EOSType.Win2008
        WIN_OS_MAP[SystemUtils.IS_OS_WINDOWS_2012] = EOSType.Win2012
        WIN_OS_MAP[checkOS("Windows Server 2016", "10.0")] = EOSType.Win2016
        WIN_OS_MAP[checkOS("Windows Server 2019", "10.0")] = EOSType.Win2019
        WIN_OS_MAP[checkOS("Windows Server 2022", "10.0")] = EOSType.Win2022

        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_TIGER] = EOSType.MacOS104
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_LEOPARD] = EOSType.MacOS105
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_SNOW_LEOPARD] = EOSType.MacOS106
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_LION] = EOSType.MacOS107
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_MOUNTAIN_LION] = EOSType.MacOS108
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_MAVERICKS] = EOSType.MacOS109
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_YOSEMITE] = EOSType.MacOS1010
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_EL_CAPITAN] = EOSType.MacOS1011
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_SIERRA] = EOSType.MacOS1012
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_HIGH_SIERRA] = EOSType.Macos1013
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_MOJAVE] = EOSType.Macos1014
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_CATALINA] = EOSType.Macos1015
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_BIG_SUR] = EOSType.MacOS11
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_MONTEREY] = EOSType.MacOS12
        OSX_OS_MAP[SystemUtils.IS_OS_MAC_OSX_VENTURA] = EOSType.MacOS13
        OSX_OS_MAP[checkOS("Mac OS X", "14")] = EOSType.MacOS14

        LINUX_OS_MAP["2.2"] = EOSType.Linux22
        LINUX_OS_MAP["2.4"] = EOSType.Linux24
        LINUX_OS_MAP["2.6"] = EOSType.Linux26
        LINUX_OS_MAP["3.2"] = EOSType.Linux32
        LINUX_OS_MAP["3.5"] = EOSType.Linux35
        LINUX_OS_MAP["3.6"] = EOSType.Linux36
        LINUX_OS_MAP["3.10"] = EOSType.Linux310
        LINUX_OS_MAP["3.16"] = EOSType.Linux316
        LINUX_OS_MAP["3.18"] = EOSType.Linux318
        LINUX_OS_MAP["4.1"] = EOSType.Linux41
        LINUX_OS_MAP["4.4"] = EOSType.Linux44
        LINUX_OS_MAP["4.9"] = EOSType.Linux49
        LINUX_OS_MAP["4.14"] = EOSType.Linux414
        LINUX_OS_MAP["4.19"] = EOSType.Linux419
        LINUX_OS_MAP["5.4"] = EOSType.Linux54
        LINUX_OS_MAP["5.10"] = EOSType.Linux510

        GENERIC_LINUX_OS_MAP["3x"] = EOSType.Linux3x
        GENERIC_LINUX_OS_MAP["4x"] = EOSType.Linux4x
        GENERIC_LINUX_OS_MAP["5x"] = EOSType.Linux5x
        GENERIC_LINUX_OS_MAP["6x"] = EOSType.Linux6x
        GENERIC_LINUX_OS_MAP["7x"] = EOSType.Linux7x
    }

    @JvmStatic
    val OSType: EOSType
        // Sorted in history order by each OS release.
        get() {
            // Windows
            if (SystemUtils.IS_OS_WINDOWS) {
                for ((key, value) in WIN_OS_MAP) {
                    if (key) {
                        return value
                    }
                }

                return EOSType.WinUnknown
            }

            // Mac OS
            if (SystemUtils.IS_OS_MAC) {
                for ((key, value) in OSX_OS_MAP) {
                    if (key) {
                        return value
                    }
                }

                return EOSType.MacOSUnknown
            }

            // Android
            if (JAVA_RUNTIME != null && JAVA_RUNTIME.startsWith("Android")) {
                return EOSType.AndroidUnknown
            }

            // Linux
            if (SystemUtils.IS_OS_LINUX) {
                val linuxOsVersion = getSystemProperty("os.version")
                    ?: return EOSType.LinuxUnknown

                val osVersion = linuxOsVersion.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                if (osVersion.size < 2) {
                    return EOSType.LinuxUnknown
                }

                val version = osVersion[0] + "." + osVersion[1]

                val linuxVersion: EOSType? = LINUX_OS_MAP[version]
                if (linuxVersion != null) {
                    // Found Major/Minor version
                    return linuxVersion
                }

                val majorVersion = osVersion[0] + "x"
                for ((key, value) in GENERIC_LINUX_OS_MAP) {
                    if (key == majorVersion) {
                        // Found generic Linux version
                        return value
                    }
                }

                return EOSType.LinuxUnknown
            }

            // Unknown OS
            return EOSType.Unknown
        }

    private fun checkOS(namePrefix: String, versionPrefix: String): Boolean {
        return SystemUtils.OS_NAME.startsWith(namePrefix) && SystemUtils.OS_VERSION.startsWith(versionPrefix)
    }

    private fun getSystemProperty(property: String): String? {
        return try {
            System.getProperty(property)
        } catch (ex: SecurityException) {
            // we are not allowed to look at this property
            null
        }
    }

    /**
     * Convenience method for calculating the CRC2 checksum of a string.
     * @param s the string
     * @return long value of the CRC32
     */
    @JvmStatic
    fun crc32(s: String): Long {
        val checksum: Checksum = CRC32()
        val bytes = s.toByteArray()
        checksum.update(bytes, 0, bytes.size)
        return checksum.value
    }
}
