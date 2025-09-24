package `in`.dragonbra.javasteam.depotdownloader

import okio.buffer
import okio.gzip
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

class AccountSettingsStore private constructor() : Serializable {

    // Using ConcurrentHashMap as Kotlin equivalent to ConcurrentDictionary
    val contentServerPenalty: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

    @Transient
    private var fileName: String = ""

    companion object {
        private const val serialVersionUID = 1L

        @Volatile
        private var _instance: AccountSettingsStore? = null

        val instance: AccountSettingsStore?
            get() = _instance

        private val isLoaded: Boolean
            get() = _instance != null

        fun loadFromFile(filePath: String) {
            if (isLoaded) {
                throw Exception("Config already loaded")
            }

            val file = File(filePath)

            _instance = if (file.exists()) {
                try {
                    file.source().use { fileSource ->
                        fileSource.gzip().buffer().use { bufferedSource ->
                            ObjectInputStream(bufferedSource.inputStream()).use { ois ->
                                ois.readObject() as AccountSettingsStore
                            }
                        }
                    }
                } catch (ex: Exception) {
                    println("Failed to load account settings: ${ex.message}")
                    AccountSettingsStore()
                }
            } else {
                AccountSettingsStore()
            }

            _instance?.fileName = filePath
        }

        fun save() {
            val currentInstance = _instance ?: throw Exception("Saved config before loading")

            try {
                val file = File(currentInstance.fileName)
                file.sink().use { fileSink ->
                    fileSink.gzip().buffer().use { bufferedSink ->
                        ObjectOutputStream(bufferedSink.outputStream()).use { oos ->
                            oos.writeObject(currentInstance)
                        }
                    }
                }
            } catch (ex: IOException) {
                println("Failed to save account settings: ${ex.message}")
            }
        }
    }
}
