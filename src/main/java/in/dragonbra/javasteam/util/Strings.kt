package `in`.dragonbra.javasteam.util

import java.math.BigInteger

/**
 * @author lngtr
 * @since 2018-02-19
 */
object Strings {

    private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()

    /**
     * the constant 2^64
     */
    private val TWO_64 = BigInteger.ONE.shiftLeft(64)

    @JvmStatic
    fun isNullOrEmpty(str: String?): Boolean = str.isNullOrEmpty()

    @JvmStatic
    fun asUnsignedDecimalString(l: Long): String {
        val b = BigInteger.valueOf(l)
        if (b.signum() < 0) {
            return b.add(TWO_64).toString()
        }

        return b.toString()
    }

    @JvmStatic
    fun toHex(bytes: ByteArray?): String {
        if (bytes == null) return ""

        return buildString(bytes.size * 2) {
            for (byte in bytes) {
                val v = byte.toInt() and 0xFF
                append(HEX_ARRAY[v shr 4])
                append(HEX_ARRAY[v and 0x0F])
            }
        }
    }

    @JvmStatic
    fun decodeHex(s: String): ByteArray {
        if (s.length % 2 != 0) {
            throw StringIndexOutOfBoundsException("Hex string must have even length")
        }

        return ByteArray(s.length / 2) { i ->
            val index = i * 2
            ((s[index].digitToInt(16) shl 4) + s[index + 1].digitToInt(16)).toByte()
        }
    }
}
