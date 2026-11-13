package `in`.dragonbra.javasteam.util

// See https://www.rfc-editor.org/rfc/rfc1950.html
object Adler32 {

    /**
     * Base modulo value - largest prime smaller than 65536
     */
    private const val BASE = 65521

    /**
     * NMAX is the largest n such that 255n(n+1)/2 + (n+1)(BASE-1) <= 2^32-1
     */
    private const val NMAX = 5552

    /**
     * Calculates the Adler32 checksum with the bytes taken from the span using `one` as the initial seed.
     */
    @JvmStatic
    fun calculate(buffer: ByteArray): Int = calculate(0, buffer)

    /**
     * Calculates the Adler32 checksum with the bytes taken from the [ByteArray]
     * @param adler The input Adler32 value. (use 1 for initial calculation)
     * @param buffer The byte array to process
     * @return The updated Adler-32 checksum
     */
    @JvmStatic
    fun calculate(adler: Int, buffer: ByteArray): Int = calculate(adler, buffer, 0, buffer.size)

    /**
     * Calculates the Adler32 checksum over a range of the [ByteArray], without copying it.
     * @param adler The input Adler32 value. (use 1 for initial calculation)
     * @param buffer The byte array to process
     * @param offset Index of the first byte to process
     * @param length Number of bytes to process
     * @return The updated Adler-32 checksum
     */
    @JvmStatic
    fun calculate(adler: Int, buffer: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset <= buffer.size - length) {
            "Range (offset $offset, length $length) is outside the buffer (size ${buffer.size})."
        }

        var s1 = (adler and 0xFFFF).toLong()
        var s2 = ((adler ushr 16) and 0xFFFF).toLong()

        var offset = offset
        val end = offset + length

        while (offset < end) {
            val k = minOf(end - offset, NMAX)
            val chunkEnd = offset + k
            var remaining = k

            // Unroll by 16 bytes for maximum performance
            while (remaining >= 16) {
                s1 += (buffer[offset].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 1].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 2].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 3].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 4].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 5].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 6].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 7].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 8].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 9].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 10].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 11].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 12].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 13].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 14].toInt() and 0xFF).toLong()
                s2 += s1
                s1 += (buffer[offset + 15].toInt() and 0xFF).toLong()
                s2 += s1

                offset += 16
                remaining -= 16
            }

            for (i in 0 until remaining) {
                s1 += (buffer[offset + i].toInt() and 0xFF).toLong()
                s2 += s1
            }

            offset = chunkEnd

            s1 %= BASE
            s2 %= BASE
        }

        return ((s2 shl 16) or s1).toInt()
    }
}
