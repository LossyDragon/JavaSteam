package `in`.dragonbra.javasteam.util

import `in`.dragonbra.javasteam.enums.EOSType
import `in`.dragonbra.javasteam.types.ChunkData
import org.apache.commons.lang3.SystemUtils
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.ClosedChannelException
import java.util.zip.CRC32
import java.util.zip.Checksum

/**
 * @author lngtr
 * @since 2018-02-23
 */
object Utils {

    private val JAVA_RUNTIME: String? = getSystemProperty("java.runtime.name")

    private val WIN_OS_MAP: Map<Boolean, EOSType> = mapOf(
        SystemUtils.IS_OS_WINDOWS_95 to EOSType.Win95,
        SystemUtils.IS_OS_WINDOWS_98 to EOSType.Win98,
        SystemUtils.IS_OS_WINDOWS_ME to EOSType.WinME,
        SystemUtils.IS_OS_WINDOWS_NT to EOSType.WinNT,
        SystemUtils.IS_OS_WINDOWS_2000 to EOSType.Win2000,
        SystemUtils.IS_OS_WINDOWS_XP to EOSType.WinXP,
        SystemUtils.IS_OS_WINDOWS_VISTA to EOSType.WinVista,
        SystemUtils.IS_OS_WINDOWS_7 to EOSType.Windows7,
        SystemUtils.IS_OS_WINDOWS_8 to EOSType.Windows8,
        SystemUtils.IS_OS_WINDOWS_10 to EOSType.Windows10,
        SystemUtils.IS_OS_WINDOWS_11 to EOSType.Win11,
        SystemUtils.IS_OS_WINDOWS_2003 to EOSType.Win2003,
        SystemUtils.IS_OS_WINDOWS_2008 to EOSType.Win2008,
        SystemUtils.IS_OS_WINDOWS_2012 to EOSType.Win2012,
        checkOS("Windows Server 2016", "10.0") to EOSType.Win2016,
        checkOS("Windows Server 2019", "10.0") to EOSType.Win2019,
        checkOS("Windows Server 2022", "10.0") to EOSType.Win2022
    )

    private val OSX_OS_MAP: Map<Boolean, EOSType> = mapOf(
        SystemUtils.IS_OS_MAC_OSX_TIGER to EOSType.MacOS104,
        SystemUtils.IS_OS_MAC_OSX_LEOPARD to EOSType.MacOS105,
        SystemUtils.IS_OS_MAC_OSX_SNOW_LEOPARD to EOSType.MacOS106,
        SystemUtils.IS_OS_MAC_OSX_LION to EOSType.MacOS107,
        SystemUtils.IS_OS_MAC_OSX_MOUNTAIN_LION to EOSType.MacOS108,
        SystemUtils.IS_OS_MAC_OSX_MAVERICKS to EOSType.MacOS109,
        SystemUtils.IS_OS_MAC_OSX_YOSEMITE to EOSType.MacOS1010,
        SystemUtils.IS_OS_MAC_OSX_EL_CAPITAN to EOSType.MacOS1011,
        SystemUtils.IS_OS_MAC_OSX_SIERRA to EOSType.MacOS1012,
        SystemUtils.IS_OS_MAC_OSX_HIGH_SIERRA to EOSType.Macos1013,
        SystemUtils.IS_OS_MAC_OSX_MOJAVE to EOSType.Macos1014,
        SystemUtils.IS_OS_MAC_OSX_CATALINA to EOSType.Macos1015,
        SystemUtils.IS_OS_MAC_OSX_BIG_SUR to EOSType.MacOS11,
        SystemUtils.IS_OS_MAC_OSX_MONTEREY to EOSType.MacOS12,
        SystemUtils.IS_OS_MAC_OSX_VENTURA to EOSType.MacOS13,
        SystemUtils.IS_OS_MAC_OSX_SONOMA to EOSType.MacOS14,
        checkOS("Mac OS X", "15") to EOSType.MacOS15
    )

    private val LINUX_OS_MAP: Map<String, EOSType> = mapOf(
        "2.2" to EOSType.Linux22,
        "2.4" to EOSType.Linux24,
        "2.6" to EOSType.Linux26,
        "3.2" to EOSType.Linux32,
        "3.5" to EOSType.Linux35,
        "3.6" to EOSType.Linux36,
        "3.10" to EOSType.Linux310,
        "3.16" to EOSType.Linux316,
        "3.18" to EOSType.Linux318,
        "4.1" to EOSType.Linux41,
        "4.4" to EOSType.Linux44,
        "4.9" to EOSType.Linux49,
        "4.14" to EOSType.Linux414,
        "4.19" to EOSType.Linux419,
        "5.4" to EOSType.Linux54,
        "5.10" to EOSType.Linux510
    )

    private val GENERIC_LINUX_OS_MAP: Map<String, EOSType> = mapOf(
        "3x" to EOSType.Linux3x,
        "4x" to EOSType.Linux4x,
        "5x" to EOSType.Linux5x,
        "6x" to EOSType.Linux6x,
        "7x" to EOSType.Linux7x
    )

    @JvmStatic
    val oSType: EOSType
        get() = when {
            SystemUtils.IS_OS_WINDOWS -> WIN_OS_MAP.entries.firstOrNull { it.key }?.value ?: EOSType.WinUnknown
            SystemUtils.IS_OS_MAC -> OSX_OS_MAP.entries.firstOrNull { it.key }?.value ?: EOSType.MacOSUnknown
            JAVA_RUNTIME?.startsWith("Android") == true -> EOSType.AndroidUnknown
            SystemUtils.IS_OS_LINUX -> getLinuxOSType()
            else -> EOSType.Unknown
        }

    private fun getLinuxOSType(): EOSType {
        val linuxOsVersion = getSystemProperty("os.version") ?: return EOSType.LinuxUnknown

        val osVersion = linuxOsVersion.split(".")
        if (osVersion.size < 2) return EOSType.LinuxUnknown

        val version = "${osVersion[0]}.${osVersion[1]}"

        // Try exact version match first
        LINUX_OS_MAP[version]?.let { return it }

        // Try generic version match
        val majorVersion = "${osVersion[0]}x"
        return GENERIC_LINUX_OS_MAP[majorVersion] ?: EOSType.LinuxUnknown
    }

    private fun checkOS(namePrefix: String, versionPrefix: String): Boolean =
        SystemUtils.OS_NAME.startsWith(namePrefix) && SystemUtils.OS_VERSION.startsWith(versionPrefix)

    private fun getSystemProperty(property: String): String? = try {
        System.getProperty(property)
    } catch (_: SecurityException) {
        null
    }

    /**
     * Convenience method for calculating the CRC2 checksum of a string.
     * @param s the string
     * @return long value of the CRC32
     */
    @JvmStatic
    fun crc32(s: String): Long = crc32(s.toByteArray())

    /**
     * Convenience method for calculating the CRC2 checksum of a byte array.
     * @param bytes the byte array
     * @return long value of the CRC32
     */
    @JvmStatic
    fun crc32(bytes: ByteArray): Long {
        val checksum: Checksum = CRC32()
        checksum.update(bytes, 0, bytes.size)
        return checksum.value
    }

    /**
     * Performs an Adler32 on the given input
     */
    @JvmStatic
    fun adlerHash(input: ByteArray): Int {
        var a = 0
        var b = 0
        for (value in input) {
            a = (a + (value.toInt() and 0xFF)) % 65521
            b = (b + a) % 65521
        }
        return a or (b shl 16)
    }

    /**
     * Validate a file against Steam3 Chunk data
     * @param fs        FileInputStream to read from
     * @param chunkData Array of ChunkData to validate against
     * @return List of ChunkData that are needed
     * @throws IOException              If there's an error reading the file
     * @throws ClosedChannelException   If this channel is closed
     * @throws IllegalArgumentException If the new position is negative
     */
    @JvmStatic
    @Throws(IOException::class, ClosedChannelException::class, IllegalArgumentException::class)
    fun validateSteam3FileChecksums(fs: RandomAccessFile, chunkData: Array<ChunkData>): List<ChunkData> = chunkData.mapNotNull { data ->
        val chunk = ByteArray(data.uncompressedLength)
        fs.channel.position(data.offset)
        val bytesRead = fs.read(chunk, 0, data.uncompressedLength)

        val actualChunk = if (bytesRead > 0 && bytesRead < data.uncompressedLength) {
            chunk.copyOf(bytesRead)
        } else {
            chunk
        }

        val adler = adlerHash(actualChunk)
        if (adler != data.checksum) data else null
    }
}
