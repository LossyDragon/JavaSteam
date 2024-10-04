package `in`.dragonbra.javasteam.util

import kotlin.jvm.Throws

/**
 * @author lngtr
 * @since 2018-02-19
 */
object Strings {
    @JvmStatic
    fun isNullOrEmpty(str: String?): Boolean = str.isNullOrEmpty()

    @JvmStatic
    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    @Throws(IllegalArgumentException::class)
    @JvmStatic
    fun decodeHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have an even length" }

        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
