package `in`.dragonbra.javasteam.depotdownloader

import com.google.protobuf.TextFormat
import `in`.dragonbra.javasteam.depotdownloader.protobufs.DepotConfigProto
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class DepotConfigStore {
    private lateinit var filePath: Path

    var installedManifestIDs: MutableMap<Int, Long> = mutableMapOf()
        private set

    companion object {
        var instance: DepotConfigStore? = null

        private val isLoaded
            get() = instance != null

        fun loadFromFile(fileName: String) {
            if (isLoaded) {
                throw Exception("Config already loaded")
            }

            val path = Paths.get(fileName)

            if (Files.exists(path)) {
                try {
                    val settings = DepotConfigProto.DepotConfig
                        .newBuilder()
                        .apply {
                            Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                                TextFormat.merge(reader, this)
                            }
                        }.build()
                    instance = DepotConfigStore().apply {
                        installedManifestIDs = settings.installedManifestIdsMap.toMutableMap()
                    }
                } catch (e: Exception) {
                    logI("Error loading settings: ${e.message}, creating new instance")
                    instance = DepotConfigStore()
                }
            } else {
                logI("Config file does not exist, creating new instance")
                instance = DepotConfigStore()
            }

            logI("Loaded file: $fileName")
            instance!!.filePath = path
        }

        fun save() {
            if (!isLoaded) {
                throw Exception("Saved config before loading")
            }

            try {
                val settings = DepotConfigProto.DepotConfig
                    .newBuilder()
                    .apply { putAllInstalledManifestIds(instance!!.installedManifestIDs) }
                    .build()

                val proto = TextFormat.printer().printToString(settings)

                Files.createDirectories(instance!!.filePath.parent)

                Files.write(
                    instance!!.filePath,
                    proto.toByteArray(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
            } catch (e: Exception) {
                logI("Error saving settings: ${e.message}")
            }
        }
    }
}
