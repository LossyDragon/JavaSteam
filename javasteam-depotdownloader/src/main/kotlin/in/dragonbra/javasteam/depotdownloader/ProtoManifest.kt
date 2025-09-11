package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.enums.EDepotFileFlag
import `in`.dragonbra.javasteam.types.DepotManifest
import kotlinx.io.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.File
import java.nio.ByteBuffer
import java.time.Instant
import java.util.EnumSet
import kotlin.io.path.*

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ProtoManifest(
    @ProtoNumber(1) val files: MutableList<FileData> = mutableListOf(),
    @ProtoNumber(2) val id: ULong = 0UL,
    @ProtoNumber(3) val creationTime: Long = Instant.now().epochSecond
) {

    // Secondary constructor for converting from DepotManifest
    constructor(sourceManifest: DepotManifest, id: ULong) : this(
        files = sourceManifest.files.map { FileData(it) }.toMutableList(),
        id = id,
        creationTime = sourceManifest.creationTime.time / 1000
    )

    @Suppress("ArrayInDataClass")
    @Serializable
    data class FileData(
        @ProtoNumber(1) val fileName: String = "",
        @ProtoNumber(2) val chunks: MutableList<ChunkData> = mutableListOf(),
        @ProtoNumber(3) val flags: EnumSet<EDepotFileFlag> = EnumSet.noneOf(EDepotFileFlag::class.java),
        @ProtoNumber(4) val totalSize: ULong = 0UL,
        @ProtoNumber(5) val fileHash: ByteArray = byteArrayOf()
    ) {

        constructor(sourceData: FileData) : this(
            fileName = sourceData.fileName,
            chunks = sourceData.chunks.map { ChunkData(it) }.toMutableList(),
            flags = sourceData.flags,
            totalSize = sourceData.totalSize,
            fileHash = sourceData.fileHash
        )
    }

    @Serializable
    data class ChunkData(
        @ProtoNumber(1) val chunkId: ByteArray,
        @ProtoNumber(2) val checksum: ByteArray,
        @ProtoNumber(3) val offset: ULong,
        @ProtoNumber(4) val compressedLength: UInt,
        @ProtoNumber(5) val uncompressedLength: UInt
    ) {
        constructor(sourceChunk:   ChunkData) : this(
            chunkId = sourceChunk.chunkId,
            checksum = ByteBuffer.allocate(4).putInt(sourceChunk.checksum).array(),
            offset = sourceChunk.offset,
            compressedLength = sourceChunk.compressedLength,
            uncompressedLength = sourceChunk.uncompressedLength
        )
    }

    companion object {
        fun loadFromFile(filename: String): Pair<ProtoManifest?, ByteArray?> {
            val file = File(filename)
            if (!file.exists()) {
                return null to null
            }

            return try {
                // Read and decompress the file using Apache Commons Compress
                val decompressedData = file.inputStream().use { fileStream ->
                    DeflateCompressorInputStream(fileStream).use { deflateStream ->
                        deflateStream.readBytes()
                    }
                }

                // Calculate SHA-1 checksum using Apache Commons Codec
                val checksum = DigestUtils.sha1(decompressedData)

                // Deserialize using kotlinx.serialization
                val manifest = ProtoBuf.decodeFromByteArray<ProtoManifest>(decompressedData)

                manifest to checksum
            } catch (e: Exception) {
                null to null
            }
        }
    }

    fun saveToFile(filename: String): ByteArray {
        // Serialize using kotlinx.serialization
        val serializedData = ProtoBuf.encodeToByteArray(this)

        // Calculate SHA-1 checksum using Apache Commons Codec
        val checksum = DigestUtils.sha1(serializedData)

        // Compress and write using Apache Commons Compress and kotlinx.io
        File(filename).outputStream().use { fileStream ->
            DeflateCompressorOutputStream(fileStream).use { deflateStream ->
                deflateStream.write(serializedData)
            }
        }

        return checksum
    }

    fun convertToSteamManifest(depotId: UInt): DepotManifest {
        var uncompressedSize = 0UL
        var compressedSize = 0UL

        val convertedFiles = files.map { file ->
            // Calculate file name hash using Apache Commons Codec
            val fileNameHash = DigestUtils.sha1(
                file.fileName.replace('/', '\\').lowercase().toByteArray(Charsets.UTF_8)
            )

            val convertedChunks = file.chunks.map { chunk ->
                val checksumInt = ByteBuffer.wrap(chunk.checksum).int

                uncompressedSize += chunk.uncompressedLength.toULong()
                compressedSize += chunk.compressedLength.toULong()

                ChunkData(
                    chunkId = chunk.chunkId,
                    checksum = checksumInt,
                    offset = chunk.offset,
                    compressedLength = chunk.compressedLength,
                    uncompressedLength = chunk.uncompressedLength
                )
            }.toMutableList()

            FileData(
                fileName = file.fileName,
                fileNameHash = fileNameHash,
                flags = file.flags,
                totalSize = file.totalSize,
                fileHash = file.fileHash,
                chunks = convertedChunks
            )
        }.toMutableList()

        return DepotManifest(
            files = convertedFiles,
            filenamesEncrypted = false,
            depotId = depotId,
            manifestGid = id,
            creationTime = Instant.ofEpochSecond(creationTime),
            totalUncompressedSize = uncompressedSize,
            totalCompressedSize = compressedSize,
            encryptedCrc = 0U
        )
    }
}
