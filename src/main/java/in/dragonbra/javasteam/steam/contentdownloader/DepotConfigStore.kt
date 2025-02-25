package `in`.dragonbra.javasteam.steam.contentdownloader

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

class DepotConfigStore {

    private var fileName: String? = null

    var installedManifestIDs = mutableMapOf<Int, Long>() // Keep as a map
        private set

    companion object {
        var instance: DepotConfigStore? = null

        val loaded: Boolean
            get() = instance != null

        @JvmStatic
        fun save() {
            if (!loaded) {
                throw Exception("Saved config before loading")
            }

            FileOutputStream(instance!!.fileName!!).use { fileStream ->
                DeflaterOutputStream(fileStream).use { deflaterStream ->
                    // Convert the map to a protobuf message for serialization
                    val builder = DepotConfigStoreProtos.DepotConfigStore.newBuilder()

                    for ((depotId, manifestId) in instance!!.installedManifestIDs) {
                        val entry = DepotConfigStoreProtos.DepotConfigStore.ManifestEntry.newBuilder()
                            .setDepotId(depotId)
                            .setManifestId(manifestId)
                            .build()
                        builder.addInstalledManifestIds(entry)
                    }

                    val bytes = builder.build().toByteArray()
                    deflaterStream.write(bytes)
                    deflaterStream.flush()
                }
            }
        }

        @JvmStatic
        fun loadFromFile(filename: String) {
            if (loaded) {
                throw Exception("Config already loaded")
            }

            if (File(filename).exists()) {
                FileInputStream(filename).use { fileStream ->
                    InflaterInputStream(fileStream).use { inflaterStream ->
                        val protobufMessage = DepotConfigStoreProtos.DepotConfigStore.parseFrom(inflaterStream.readBytes())

                        // Convert the protobuf message to our map
                        for (entry in protobufMessage.installedManifestIdsList) {
                            instance!!.installedManifestIDs[entry.depotId] = entry.manifestId
                        }
                    }
                }
            } else {
                instance = DepotConfigStore()
            }

            instance!!.fileName = filename
        }
    }
}
