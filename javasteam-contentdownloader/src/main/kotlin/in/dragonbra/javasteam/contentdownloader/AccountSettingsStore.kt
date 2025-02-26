package `in`.dragonbra.javasteam.contentdownloader

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.*

@Serializable
class AccountSettingsStore {

    @Transient
    private lateinit var fileName: String

    private val _contentServerPenalty: MutableMap<String, Int> = mutableMapOf()

    @Transient
    val contentServerPenalty: ConcurrentHashMap<String, Int> = ConcurrentHashMap<String, Int>().also { map ->
        map.putAll(_contentServerPenalty)
    }
    val loginTokens: MutableMap<String, String> = mutableMapOf()
    val guardData: MutableMap<String, String> = mutableMapOf()

    companion object {
        var instance: AccountSettingsStore? = null
            private set

        private val loaded: Boolean
            get() = instance != null

        fun loadFromFile(filename: String) {
            if (loaded) {
                throw Exception("Config already loaded")
            }

            val file = File(filename)
            instance = if (file.exists()) {
                try {
                    file.inputStream().use { fileStream ->
                        DeflaterInputStream(fileStream).use { deflateStream ->
                            val json = Json { ignoreUnknownKeys = true }
                            json.decodeFromString<AccountSettingsStore>(
                                deflateStream.readBytes().toString(Charsets.UTF_8)
                            )
                        }
                    }
                } catch (ex: Exception) {
                    println("Failed to load account settings: ${ex.message}")
                    AccountSettingsStore()
                }
            } else {
                AccountSettingsStore()
            }

            instance!!.fileName = filename
        }

        fun save() {
            if (!loaded) {
                throw Exception("Saved config before loading")
            }

            try {
                File(instance!!.fileName).outputStream().use { fileStream ->
                    DeflaterOutputStream(fileStream).use { deflateStream ->
                        val json = Json { prettyPrint = false }
                        val jsonString = json.encodeToString(instance)
                        deflateStream.write(jsonString.toByteArray(Charsets.UTF_8))
                        deflateStream.flush()
                    }
                }
            } catch (ex: Exception) {
                println("Failed to save account settings: ${ex.message}")
            }
        }
    }
}
