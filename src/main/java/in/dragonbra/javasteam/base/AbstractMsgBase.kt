package `in`.dragonbra.javasteam.base

import `in`.dragonbra.javasteam.util.stream.BinaryReader
import `in`.dragonbra.javasteam.util.stream.BinaryWriter
import `in`.dragonbra.javasteam.util.stream.MemoryStream
import `in`.dragonbra.javasteam.util.stream.SeekOrigin
import java.io.IOException
import java.nio.charset.Charset

/**
 * This class provides a payload backing to client messages.
 *
 * @constructor Initializes a new instance of the [AbstractMsgBase] class.
 * @param payloadReserve The number of bytes to initialize the payload capacity to.
 */
abstract class AbstractMsgBase @JvmOverloads constructor(payloadReserve: Int = 0) {

    /**
     * Gets a [MemoryStream] which is the backing stream for client message payload data.
     * @return a memory stream which is the backing stream for client message payload data.
     */
    val payload: MemoryStream = MemoryStream(payloadReserve)

    private val reader = BinaryReader(payload)

    private val writer = BinaryWriter(payload.asOutputStream())

    /**
     * Seeks within the payload to the specified offset.
     * @param offset     The offset in the payload to seek to.
     * @param seekOrigin The origin to seek from.
     * @return The new position within the stream, calculated by combining the initial reference point and the offset.
     */
    fun seek(offset: Long, seekOrigin: SeekOrigin): Long = payload.seek(offset, seekOrigin)

    /**
     * Writes a single [Byte] to the message payload.
     * @param data The byte.
     */
    @Throws(IOException::class)
    fun write(data: Byte) {
        writer.write(data.toInt())
    }

    /**
     * Writes a single [Short] to the message payload.
     * @param data The short.
     */
    @Throws(IOException::class)
    fun write(data: Short) {
        writer.writeShort(data)
    }

    /**
     * Writes a single [Int] to the message payload.
     * @param data The int.
     */
    @Throws(IOException::class)
    fun write(data: Int) {
        writer.writeInt(data)
    }

    /**
     * Writes a single [Long] to the message payload.
     * @param data The long.
     */
    @Throws(IOException::class)
    fun write(data: Long) {
        writer.writeLong(data)
    }

    /**
     * Writes the specified [ByteArray] to the message payload.
     * @param data The byte array.
     */
    @Throws(IOException::class)
    fun write(data: ByteArray) {
        writer.write(data)
    }

    /**
     * Writes a single [Float] to the message payload.
     * @param data The float.
     */
    @Throws(IOException::class)
    fun write(data: Float) {
        writer.writeFloat(data)
    }

    /**
     * Writes a single [Double] to the message payload.
     * @param data The double.
     */
    @Throws(IOException::class)
    fun write(data: Double) {
        writer.writeDouble(data)
    }

    /**
     * Writes the specified string to the message payload using default encoding.
     * This function does not write a terminating null character.
     * @param data The string to write.
     * @param charset The encoding to use. Defaults to [Charset.defaultCharset].
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun write(data: String, charset: Charset = Charset.defaultCharset()) {
        write(data.toByteArray(charset))
    }

    /**
     * Writes the specified string and a null terminator to the message payload using default encoding.
     * @param data The string to write.
     * @param charset The encoding to use. Defaults to [Charset.defaultCharset].
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun writeNullTermString(data: String, charset: Charset = Charset.defaultCharset()) {
        write(data, charset)
        write("\u0000", charset)
    }

    /**
     * Reads a single [Byte] from the message payload.
     * @return The byte.
     */
    @Throws(IOException::class)
    fun readByte(): Byte {
        return reader.readByte()
    }

    /**
     * Reads a number of bytes from the message payload.
     * @param numBytes The number of bytes to read.
     * @return The data.
     */
    @Throws(IOException::class)
    fun readBytes(numBytes: Int): ByteArray {
        return reader.readBytes(numBytes)
    }

    /**
     * Reads a single [Short] from the message payload.
     * @return The short.
     */
    @Throws(IOException::class)
    fun readShort(): Short {
        return reader.readShort()
    }

    /**
     * Reads a single [Int] from the message payload.
     * @return The int.
     */
    @Throws(IOException::class)
    fun readInt(): Int {
        return reader.readInt()
    }

    /**
     * Reads a single [Long] from the message payload.
     * @return The long.
     */
    @Throws(IOException::class)
    fun readLong(): Long {
        return reader.readLong()
    }

    /**
     * Reads a single [Float] from the message payload.
     * @return The float.
     */
    @Throws(IOException::class)
    fun readFloat(): Float {
        return reader.readFloat()
    }

    /**
     * Reads a single [Double] from the message payload.
     * @return The double.
     */
    @Throws(IOException::class)
    fun readDouble(): Double {
        return reader.readDouble()
    }

    /**
     * Reads a null terminated string from the message payload with the default encoding.
     * @param charset The encoding to use. Defaults to [Charset.defaultCharset].
     * @return The string.
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun readNullTermString(charset: Charset = Charset.defaultCharset()): String {
        return reader.readNullTermString(charset)
    }
}
