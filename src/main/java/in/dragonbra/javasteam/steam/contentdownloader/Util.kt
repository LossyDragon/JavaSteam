package `in`.dragonbra.javasteam.steam.contentdownloader

import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.util.log.LogManager
import org.apache.commons.lang3.SystemUtils
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal object Util {

    private val logger = LogManager.getLogger(Util::class.java)

    @JvmStatic
    fun getSteamOS(): String = when {
        SystemUtils.IS_OS_WINDOWS -> "windows"
        SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_MAC_OSX -> "macos"
        SystemUtils.IS_OS_LINUX -> "linux"
        SystemUtils.IS_OS_FREE_BSD -> "linux" // Return linux as freebsd steam client doesn't exist yet
        else -> "unknown"
    }

    @JvmStatic
    fun getSteamArch(): String = if (SystemUtils.OS_ARCH.contains("64")) "64" else "32"

    @JvmStatic
    fun loadManifestFromFile(
        directory: String,
        depotId: Int,
        manifestId: Long,
        badHashWarning: Boolean
    ): DepotManifest? {

        // Try loading Steam format manifest first
        var filename = File(directory, "${depotId}_${manifestId}.manifest").path

        if (File(filename).exists()) {
            val expectedChecksum: ByteArray? = try {
                File("$filename.sha").readBytes()
            } catch (_: IOException) {
                null
            }

            val currentChecksum = fileSHAHash(filename)
            if (expectedChecksum != null && expectedChecksum.contentEquals(currentChecksum)) {
                return DepotManifest.loadFromFile(filename)
            } else if (badHashWarning) {
                logger.error("Manifest $manifestId on disk did not match the expected checksum.")
            }
        }

        // No legacy manifest format

        return null
    }

    @JvmStatic
    fun fileSHAHash(filename: String): ByteArray {
        File(filename).inputStream().use { fs ->
            val sha = MessageDigest.getInstance("SHA-1")
            return sha.digest(fs.readBytes())
        }
    }
}
