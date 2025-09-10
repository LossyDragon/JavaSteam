package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.protobufs.depotdownloader.Protomanifest
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.util.crypto.CryptoHelper
import `in`.dragonbra.javasteam.util.log.LogManager
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.InflaterInputStream

class ProtoManifest(sourceManifest: DepotManifest, id: ULong) {

    val files: MutableList<Protomanifest.FileData> = mutableListOf()

    var id: ULong = 0uL
        private set

    val creationTime: Instant = Instant.ofEpochSecond(0)

    companion object {

        private val logger = LogManager.getLogger(Protomanifest::class.java)

        /**
         * TODO
         * @return a [Pair] of [ProtoManifest] and a checksum of [ByteArray]
         */
        @JvmStatic
        fun loadFromFile(filename: String): Pair<ProtoManifest?, ByteArray?> {
            val file = File(filename)

            if (!file.exists()) {
                return Pair(null, null)
            }

            try {
                val data = file.inputStream().use { fs ->
                    InflaterInputStream(fs).use { inflater ->
                        inflater.readBytes()
                    }
                }

                val checksum = MessageDigest.getInstance("SHA-1", CryptoHelper.SEC_PROV).digest(data)

                val protoManifest = Protomanifest.ProtoManifest.parseFrom(data)

                return ProtoManifest(protoManifest) to checksum
            } catch (e: Exception) {
                logger.error("An error occured while loading from file", e)
                return Pair(null, null)
            }
        }
    }
}
