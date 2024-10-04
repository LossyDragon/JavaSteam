package `in`.dragonbra.javasteam.util

import java.lang.StringBuilder
import java.nio.charset.StandardCharsets

/**
 * @author lngtr
 * @since 2018-04-16
 */
object WebHelpers {

    @JvmStatic
    fun isUrlSafeChar(ch: Char): Boolean =
        ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_'

    @JvmStatic
    fun urlEncode(input: String): String = urlEncode(input.toByteArray(StandardCharsets.UTF_8))

    @JvmStatic
    fun urlEncode(input: ByteArray): String {
        val encoded = StringBuilder(input.size * 2)

        for (i in input) {
            val ch = i.toInt().toChar()

            when {
                isUrlSafeChar(ch) -> encoded.append(ch)
                ch == ' ' -> encoded.append('+')
                else -> encoded.append(String.format("%%%02X", i))
            }
        }

        return encoded.toString()
    }
}
