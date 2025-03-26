package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.util.Strings
import `in`.dragonbra.javasteam.util.crypto.CryptoHelper
import org.apache.commons.lang3.SystemUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

fun logI(message: String = "") {
    println("\u001B[32m$message\u001B[0m")
}

fun logE(message: String?, throwable: Throwable? = null) {
    println("\u001B[31m${message.orEmpty()}\u001B[0m")
    throwable?.printStackTrace()
}

fun readPassword(prompt: String?): String? {
    val console = System.console()
    return if (console != null) {
        print(prompt)
        String(console.readPassword())
    } else {
        logE("Warning: Console not available, password will be visible")
        print(prompt)
        readlnOrNull()
    }
}

fun getSteamOS(): String =
    when {
        SystemUtils.IS_OS_WINDOWS -> "windows"
        SystemUtils.IS_OS_MAC -> "macos"
        SystemUtils.IS_OS_LINUX -> "linux"
        SystemUtils.IS_OS_FREE_BSD -> "linux" // Return linux as freebsd steam client doesn't exist yet
        else -> "unknown"
    }

fun getSteamArch(): String = if (SystemUtils.OS_ARCH.contains("64")) "64" else "32"

fun fileSHAHash(filename: Path): ByteArray {
    Files.newInputStream(filename).use { fileStream ->
        val sha = MessageDigest.getInstance("SHA-1")
        return sha.digest(fileStream.readBytes())
    }
}

fun loadManifestFromFile(
    directory: Path,
    depotId: Int,
    manifestId: Long,
    badHashWarning: Boolean,
): DepotManifest? {
    // Try loading Steam format manifest first.
    val filename = directory.resolve("${depotId}_$manifestId.manifest")

    if (Files.exists(filename)) {
        val expectedChecksum: ByteArray? = try {
            File("$filename.sha").readBytes()
        } catch (e: IOException) {
            logE(null, e)
            null
        }

        val currentChecksum = fileSHAHash(filename)

        if (expectedChecksum != null && expectedChecksum.contentEquals(currentChecksum)) {
            return DepotManifest.loadFromFile(filename.toString())
                ?: throw FileNotFoundException("Manifest file not found")
        } else if (badHashWarning) {
            logI("Manifest $manifestId on disk did not match the expected checksum.")
        }
    }

    return null
}

fun saveManifestToFile(directory: Path, manifest: DepotManifest): Boolean = try {
    val filename = directory.resolve("${manifest.depotID}_${manifest.manifestGID}.manifest")
    manifest.saveToFile(filename.toString())
    Files.write(
        filename.resolveSibling("${filename.fileName}.sha"),
        fileSHAHash(filename),
    )
    true
} catch (e: Exception) {
    logE(null, e)
    false
}

fun decodeHexString(hex: String): ByteArray = Strings.decodeHex(hex)

/**
 * Decrypts using AES/ECB/PKCS7
 */
fun symmetricDecryptECB(
    input: ByteArray,
    key: ByteArray,
): ByteArray {
    val cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", CryptoHelper.SEC_PROV)

    val keySpec = SecretKeySpec(key, "AES")
    cipher.init(Cipher.DECRYPT_MODE, keySpec)

    return cipher.doFinal(input)
}

fun setExecutable(path: Path, value: Boolean) {
    if (SystemUtils.IS_OS_WINDOWS) {
        return
    }

    val file = path.toFile()
    if (file.canExecute() != value) {
        file.setExecutable(value, false)
    }
}
