package `in`.dragonbra.javasteam.contentdownloader

import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.util.log.LogManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.apache.commons.lang3.SystemUtils
import java.io.File
import java.io.IOException
import java.security.MessageDigest

object Util {

    private val logger = LogManager.getLogger(Util::class.java)

    fun readPassword(): String {
        val console = System.console()
        if (console != null) {
            val passwordArray = console.readPassword("Enter password: ")
            val password = String(passwordArray)
            // Clear the password array for security
            passwordArray.fill('0')
            return password
        } else {
            // Fallback for when console is not available (e.g., in IDE)
            println("Warning: Console not available, password will be echoed")
            return readlnOrNull() ?: ""
        }
    }

    @JvmStatic
    fun getSteamOS(): String = when {
        SystemUtils.IS_OS_WINDOWS -> "windows"
        SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_MAC_OSX -> "macos"
        SystemUtils.IS_OS_LINUX -> "linux"
        SystemUtils.IS_OS_FREE_BSD -> "linux" // Return linux as freebsd steam client doesn't exist yet
        else -> "unknown"
    }

    @JvmStatic
    fun getSteamArch(): String = if (SystemUtils.OS_ARCH.contains("64")) "64" else "32"

    @JvmStatic
    fun loadManifestFromFile(
        directory: String,
        depotId: Int,
        manifestId: Long,
        badHashWarning: Boolean
    ): DepotManifest? {

        // Try loading Steam format manifest first
        var filename = File(directory, "${depotId}_${manifestId}.manifest").path

        if (File(filename).exists()) {
            val expectedChecksum: ByteArray? = try {
                File("$filename.sha").readBytes()
            } catch (_: IOException) {
                null
            }

            val currentChecksum = fileSHAHash(filename)
            if (expectedChecksum != null && expectedChecksum.contentEquals(currentChecksum)) {
                return DepotManifest.loadFromFile(filename)
            } else if (badHashWarning) {
                logger.error("Manifest $manifestId on disk did not match the expected checksum.")
            }
        }

        // No legacy manifest format

        return null
    }

    @JvmStatic
    fun fileSHAHash(filename: String): ByteArray {
        File(filename).inputStream().use { fs ->
            val sha = MessageDigest.getInstance("SHA-1")
            return sha.digest(fs.readBytes())
        }
    }

    @JvmStatic
    fun saveManifestToFile(directory: String, manifest: DepotManifest): Boolean {
        return try {
            val filename = "$directory/${manifest.depotID}_${manifest.manifestGID}.manifest"
            manifest.saveToFile(filename)
            File("$filename.sha").writeBytes(fileSHAHash(filename))
            true // If everything completes without throwing an exception, return true
        } catch (e: Exception) {
            false // Return false if an error occurs
        }
    }

    suspend fun invokeAsync(taskFactories: List<suspend () -> Unit>, maxDegreeOfParallelism: Int) {
        require(taskFactories.isNotEmpty()) { "Task factories list cannot be null or empty" }
        require(maxDegreeOfParallelism > 0) { "Max degree of parallelism must be greater than 0" }

        val queue = taskFactories.toList()
        if (queue.isEmpty()) {
            return
        }

        coroutineScope {
            val activeJobs = mutableListOf<Job>()
            var index = 0

            do {
                // Launch new coroutines until we reach the max degree of parallelism
                while (activeJobs.size < maxDegreeOfParallelism && index < queue.size) {
                    val taskFactory = queue[index++]
                    val job = launch {
                        taskFactory()
                    }
                    activeJobs.add(job)
                }

                // Wait for any job to complete
                val completedJob = activeJobs.firstOrNull { it.isCompleted }
                    ?: activeJobs.first().also {
                        // If no job is complete yet, wait for the first one
                        it.join()
                    }

                // Remove the completed job
                activeJobs.remove(completedJob)
            } while (index < queue.size || activeJobs.isNotEmpty())
        }
    }
}
