package `in`.dragonbra.javasteam.util

/**
 * @author lngtr
 * @since 2018-04-16
 */
object WebHelpers {

    private fun isUrlSafeChar(ch: Char): Boolean =
        ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_'

    @JvmStatic
    fun urlEncode(input: String): String = urlEncode(input.toByteArray(Charsets.UTF_8))

    @JvmStatic
    fun urlEncode(input: ByteArray): String = buildString(input.size * 2) {
        for (byte in input) {
            val char = byte.toInt().toChar()

            when {
                isUrlSafeChar(char) -> append(char)
                char == ' ' -> append('+')
                else -> append("%%%02X".format(byte))
            }
        }
    }
}
