@file:Suppress("unused")

package `in`.dragonbra.javasteam.util

import java.math.BigInteger

/**
 * @author lngtr
 * @since 2018-02-19
 */
object Strings {

    // the constant 2^64
    private val TWO_64: BigInteger = BigInteger.ONE.shiftLeft(64)

    private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()

    @JvmStatic
    fun asUnsignedDecimalString(l: Long): String =
        (
            if (BigInteger.valueOf(l).signum() < 0) {
                BigInteger.valueOf(l)
                    .add(TWO_64)
            } else {
                BigInteger.valueOf(l)
            }
            ).toString()

    @JvmStatic
    fun isNullOrEmpty(str: String?): Boolean = str.isNullOrEmpty()

    @JvmStatic
    fun toHex(bytes: ByteArray): String = bytes.joinToString(separator = "") {
        "${HEX_ARRAY[it.toInt() and 0xF0 shr 4]}${HEX_ARRAY[it.toInt() and 0x0F]}"
    }

    @JvmStatic
    fun decodeHex(s: String): ByteArray = ByteArray(s.length / 2) { i ->
        ((Character.digit(s[2 * i], 16) shl 4) + Character.digit(s[2 * i + 1], 16)).toByte()
    }
}
