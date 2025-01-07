package `in`.dragonbra.javasteam.steam.contentdownloader

import `in`.dragonbra.javasteam.protobufs.steam.contentdownloader.DepotConfigStore.InstalledManifestIDsMap
import `in`.dragonbra.javasteam.protobufs.steam.contentdownloader.DepotConfigStore.ManifestIDEntry
import `in`.dragonbra.javasteam.util.log.LogManager
import java.io.File
import java.io.IOException
import java.nio.file.Files

class DepotConfigStore {

    companion object {
        private val logger = LogManager.getLogger(DepotConfigStore::class.java)

        @JvmStatic
        var instance: DepotConfigStore? = null
            private set

        @JvmStatic
        val isLoaded: Boolean
            get() = instance != null

        @JvmStatic
        fun loadFromFile(fileName: String) {
            if (isLoaded) {
                throw Exception("Config already loaded")
            }

            val file = File(fileName)
            if (file.exists()) {
                Files.newInputStream(file.toPath()).use { fis ->
                    instance = DepotConfigStore()
                    instance!!.installedManifestIDs = InstalledManifestIDsMap.parseFrom(fis)
                        .installedManifestIdsList
                        .associate { it.key to it.value }
                        .toMutableMap()
                }
            } else {
                instance = DepotConfigStore()
            }

            instance!!.fileName = fileName
        }

        @JvmStatic
        fun save() {
            if (!isLoaded) {
                throw Exception("Saved config before loading")
            }

            val builder = InstalledManifestIDsMap.newBuilder().apply {
                instance!!.installedManifestIDs.map {
                    ManifestIDEntry.newBuilder()
                        .setKey(it.key)
                        .setValue(it.value)
                }
            }.build()

            try {
                Files.newOutputStream(File(instance!!.fileName).toPath()).use { fos ->
                    builder.writeTo(fos)
                }
            } catch (e: IOException) {
                logger.error("Failed to write servers to file ${instance!!.fileName}", e)
            }
        }
    }

    var installedManifestIDs: MutableMap<Int, Long> = mutableMapOf()
        private set

    lateinit var fileName: String
}
