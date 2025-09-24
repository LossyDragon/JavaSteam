package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.types.ChunkData
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.util.crypto.CryptoHelper
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeIntLe
import okio.Path
import org.apache.commons.lang3.SystemUtils
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

object Util {

    /**
     * TODO
     */
    @JvmStatic
    fun getSteamOS(): String = when {
        SystemUtils.IS_OS_WINDOWS -> "windows"
        SystemUtils.IS_OS_MAC_OSX -> "macos"
        SystemUtils.IS_OS_LINUX -> "linux"
        SystemUtils.IS_OS_FREE_BSD -> "linux" // Return linux as freebsd steam client doesn't exist yet
        else -> "unknown"
    }

    /**
     * TODO
     */
    @JvmStatic
    fun getSteamArch(): String = when {
        SystemUtils.OS_ARCH.contains("amd64") -> "64"
        SystemUtils.OS_ARCH.contains("x86_64") -> "64"
        SystemUtils.OS_ARCH.contains("aarch64") -> "64"
        else -> "32"
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
    fun fileSHAHash(filename: String): ByteArray = File(filename).inputStream().buffered().use { fs ->
        val sha = MessageDigest.getInstance("SHA-1", CryptoHelper.SEC_PROV)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead: Int

        while (fs.read(buffer).also { bytesRead = it } != -1) {
            sha.update(buffer, 0, bytesRead)
        }

        sha.digest()
    }

    /**
     *
     */
    @JvmStatic
    fun loadManifestFromFile(
        directory: Path,
        depotId: Int,
        manifestId: Long,
        badHashWarning: Boolean,
    ): DepotManifest? {
        TODO()
    }

    /**
     *
     */
    @JvmStatic
    fun saveManifestToFile(
        directory: Path,
        manifest: DepotManifest?
    ): Boolean {
        TODO()
    }
}
