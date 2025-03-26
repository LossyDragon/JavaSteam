package `in`.dragonbra.javasteam.depotdownloader

import com.google.protobuf.TextFormat
import `in`.dragonbra.javasteam.depotdownloader.protobufs.AccountSettingsProto
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class AccountSettingsStore {

    private lateinit var filePath: Path

    var guardData: MutableMap<String, String> = mutableMapOf()
        private set
    var loginTokens: MutableMap<String, String> = mutableMapOf()
        private set
    var contentServerPenalty: MutableMap<String, Int> = mutableMapOf()
        private set

    companion object {
        var instance: AccountSettingsStore? = null

        private val isLoaded
            get() = instance != null

        fun loadFromFile(fileName: String) {
            if (isLoaded) {
                throw Exception("Config already loaded")
            }

            val path = Paths.get(fileName)

            if (Files.exists(path)) {
                try {
                    val settings = AccountSettingsProto.AccountSettings.newBuilder().apply {
                        Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                            TextFormat.merge(reader, this)
                        }
                    }.build()
                    instance = AccountSettingsStore().apply {
                        guardData = settings.guardDataMap.toMutableMap()
                        loginTokens = settings.loginTokensMap.toMutableMap()
                        contentServerPenalty = settings.contentServerPenaltyMap.toMutableMap()
                    }
                } catch (e: Exception) {
                    logI("Error loading settings: ${e.message}, creating new instance")
                    instance = AccountSettingsStore()
                }
            } else {
                logI("Config file does not exist, creating new instance")
                instance = AccountSettingsStore()
            }

            logI("Loaded file: $fileName")
            instance!!.filePath = path
        }

        fun save() {
            if (!isLoaded) {
                throw Exception("Saved config before loading")
            }

            try {
                val settings = AccountSettingsProto.AccountSettings
                    .newBuilder()
                    .apply {
                        putAllGuardData(instance!!.guardData)
                        putAllLoginTokens(instance!!.loginTokens)
                        putAllContentServerPenalty(instance!!.contentServerPenalty)
                    }.build()

                val proto = TextFormat.printer().printToString(settings)

                instance!!.filePath.parent?.let(Files::createDirectories)

                Files.write(
                    instance!!.filePath,
                    proto.toByteArray(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
            } catch (e: Exception) {
                logI("Error saving settings: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
