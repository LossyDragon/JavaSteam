package `in`.dragonbra.javasteam.contentdownloader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.*

@Serializable
class AccountSettingsStore {

    @SerialName("contentServerPenalty")
    val contentServerPenalty = mutableMapOf<String, Int>()

    @SerialName("loginTokens")
    val loginTokens = mutableMapOf<String, String>()

    @SerialName("guardData")
    val guardData = mutableMapOf<String, String>()

    @Transient
    private lateinit var fileName: String

    companion object {
        var instance: AccountSettingsStore? = null
            private set

        private val json = Json { ignoreUnknownKeys = true }

        fun loadFromFile(filename: String) {
            if (instance != null) {
                throw Exception("Config already loaded")
            }

            val file = File(filename)
            instance = if (file.exists()) {
                try {
                    file.inputStream().use { fileInput ->
                        InflaterInputStream(fileInput).use { iis ->
                            json.decodeFromString(iis.bufferedReader().readText())
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
            if (instance == null) {
                throw Exception("Saved config before loading")
            }

            try {
                File(instance!!.fileName).outputStream().use { os ->
                    DeflaterOutputStream(os).use { dos ->
                        dos.write(json.encodeToString(instance).toByteArray())
                    }
                }
            } catch (ex: Exception) {
                println("Failed to save account settings: ${ex.message}")
            }
        }
    }
}
