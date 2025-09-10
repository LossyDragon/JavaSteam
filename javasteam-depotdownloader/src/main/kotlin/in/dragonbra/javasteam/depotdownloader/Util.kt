package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.types.ChunkData
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.util.crypto.CryptoHelper
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import kotlinx.io.writeIntLe
import org.apache.commons.lang3.SystemUtils
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

object Util {

    /**
     * TODO
     */
    @JvmStatic
    fun getSteamOS(): String {
        return when {
            SystemUtils.IS_OS_WINDOWS -> "windows"
            SystemUtils.IS_OS_MAC_OSX -> "macos"
            SystemUtils.IS_OS_LINUX -> "linux"
            SystemUtils.IS_OS_FREE_BSD -> "linux" // Return linux as freebsd steam client doesn't exist yet
            else -> "unknown"
        }
    }

    /**
     * TODO
     */
    @JvmStatic
    fun getSteamArch(): String {
        return when {
            SystemUtils.OS_ARCH.contains("amd64") -> "64"
            SystemUtils.OS_ARCH.contains("x86_64") -> "64"
            SystemUtils.OS_ARCH.contains("aarch64") -> "64"
            else -> "32"
        }
    }

    // fun readPassword()

    /**
     * Validate a file against Steam3 Chunk data
     */
    @JvmStatic
    fun validateSteam3FileChecksums(
        fs: RandomAccessFile,
        chunkData: Array<ChunkData>,
    ): List<ChunkData> {
        val neededChunks = mutableListOf<ChunkData>()

        for (data in chunkData) {
            fs.seek(data.offset)
            val adler = adlerHash(fs, data.uncompressedLength)

            // LE conversion
            val checksumBytes = Buffer().apply {
                writeIntLe(data.checksum)
            }.readByteArray()

            if (!adler.contentEquals(checksumBytes)) {
                neededChunks.add(data)
            }
        }

        return neededChunks
    }

    /**
     * TODO
     */
    @JvmStatic
    fun adlerHash(file: RandomAccessFile, length: Int): ByteArray {
        var a = 0u
        var b = 0u

        for (i in 0 until length) {
            val byte = file.read()
            if (byte == -1) break

            val c = byte.toUInt()
            a = (a + c) % 65521u
            b = (b + a) % 65521u
        }

        val result = a or (b shl 16)
        return Buffer().apply {
            writeIntLe(result.toInt())
        }.readByteArray()
    }

    /**
     * TODO
     */
    @JvmStatic
    fun fileSHAHash(filename: String): ByteArray {
        return File(filename).inputStream().buffered().use { fs ->
            val sha = MessageDigest.getInstance("SHA-1", CryptoHelper.SEC_PROV)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int

            while (fs.read(buffer).also { bytesRead = it } != -1) {
                sha.update(buffer, 0, bytesRead)
            }

            sha.digest()
        }
    }

    /**
     * TODO
     */
    @JvmStatic
    fun loadManifestFromFile(
        directory: String,
        depotId: UInt,
        manifestId: ULong,
        badHashWarning: Boolean
    ): DepotManifest? {
        // Try loading Steam format manifest first
        var filename = File(directory, "${depotId}_${manifestId}.manifest").absolutePath

        if (File(filename).exists()) {
            val expectedChecksum = try {
                File("$filename.sha").readBytes()
            } catch (e: IOException) {
                null
            }

            val currentChecksum = fileSHAHash(filename)

            if (expectedChecksum != null && expectedChecksum.contentEquals(currentChecksum)) {
                return DepotManifest.loadFromFile(filename)
            } else if (badHashWarning) {
                println("Manifest $manifestId on disk did not match the expected checksum.")
            }
        }

        // Try converting legacy manifest format
        filename = File(directory, "${depotId}_${manifestId}.bin").absolutePath

        if (File(filename).exists()) {
            val expectedChecksum = try {
                File("$filename.sha").readBytes()
            } catch (e: IOException) {
                null
            }

            val currentChecksum = ByteArray(0) // This will be populated by loadFromFile
            val oldManifest = ProtoManifest.loadFromFile(filename, currentChecksum)

            if (oldManifest != null && (expectedChecksum == null || !expectedChecksum.contentEquals(currentChecksum))) {
                if (badHashWarning) {
                    println("Manifest $manifestId on disk did not match the expected checksum.")
                }
                return null
            }

            return oldManifest?.convertToSteamManifest(depotId)
        }

        return null
    }
}
