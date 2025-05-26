package `in`.dragonbra.javasteam.base

import `in`.dragonbra.javasteam.util.stream.BinaryReader
import `in`.dragonbra.javasteam.util.stream.BinaryWriter
import `in`.dragonbra.javasteam.util.stream.MemoryStream
import `in`.dragonbra.javasteam.util.stream.SeekOrigin
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * This class provides a payload backing to client messages.
 *
 * @constructor
 * @param payloadReserve The number of bytes to initialize the payload capacity to.
 */
@Suppress("unused")
abstract class AbstractMsgBase @JvmOverloads constructor(
    payloadReserve: Int = 0,
) {

    val payload: MemoryStream = MemoryStream(payloadReserve)

    private val reader: BinaryReader = BinaryReader(payload)

    private val writer: BinaryWriter = BinaryWriter(payload.asOutputStream())

    /**
     * Seeks within the payload to the specified offset.
     *
     * @param offset     The offset in the payload to seek to.
     * @param seekOrigin The origin to seek from.
     * @return The new position within the stream, calculated by combining the initial reference point and the offset.
     */
    fun seek(offset: Long, seekOrigin: SeekOrigin): Long = payload.seek(offset, seekOrigin)

    @Throws(IOException::class)
    fun writeByte(data: Byte) {
        writer.write(data.toInt())
    }

    @Throws(IOException::class)
    fun readByte(): Byte = reader.readByte()

    @Throws(IOException::class)
    fun writeBytes(data: ByteArray) {
        writer.write(data)
    }

    @Throws(IOException::class)
    fun readBytes(numBytes: Int): ByteArray = reader.readBytes(numBytes)

    @Throws(IOException::class)
    fun writeShort(data: Short) {
        writer.writeShort(data)
    }

    @Throws(IOException::class)
    fun readShort(): Short = reader.readShort()

    @Throws(IOException::class)
    fun writeInt(data: Int) {
        writer.writeInt(data)
    }

    @Throws(IOException::class)
    fun readInt(): Int = reader.readInt()

    @Throws(IOException::class)
    fun writeLong(data: Long) {
        writer.writeLong(data)
    }

    @Throws(IOException::class)
    fun readLong(): Long = reader.readLong()

    @Throws(IOException::class)
    fun writeFloat(data: Float) {
        writer.writeFloat(data)
    }

    @Throws(IOException::class)
    fun readFloat(): Float = reader.readFloat()

    @Throws(IOException::class)
    fun writeDouble(data: Double) {
        writer.writeDouble(data)
    }

    @Throws(IOException::class)
    fun readDouble(): Double = reader.readDouble()

    @JvmOverloads
    @Throws(IOException::class)
    fun writeString(data: String, charset: Charset = StandardCharsets.UTF_8) {
        writeBytes(data.toByteArray(charset))
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun writeNullTermString(data: String, charset: Charset = StandardCharsets.UTF_8) {
        writeString(data, charset)
        writeString("\u0000", charset)
    }

    @JvmOverloads
    @Throws(IOException::class)
    fun readNullTermString(charset: Charset = StandardCharsets.UTF_8): String = reader.readNullTermString(charset)
}
