package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.util.log.LogManager
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

object PlatformUtilities {

    private val logger = LogManager.getLogger(PlatformUtilities::class.java)

    /**
     * TODO
     */
    @JvmStatic
    fun setExecutable(path: String, value: Boolean) {
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            return
        }

        val filePath = Paths.get(path)

        try {
            val currentPermissions = Files.getPosixFilePermissions(filePath).toMutableSet()

            val executePermissions = setOf(
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_EXECUTE
            )

            val hasExecutePermissions = currentPermissions.containsAll(executePermissions)

            if (hasExecutePermissions != value) {
                if (value) {
                    currentPermissions.addAll(executePermissions)
                } else {
                    currentPermissions.removeAll(executePermissions)
                }

                Files.setPosixFilePermissions(filePath, currentPermissions)
            }
        } catch (e: UnsupportedOperationException) {
            logger.error(e)

            File(path).also { file ->
                file.setExecutable(value)
            }
        } catch (e: IOException) {
            logger.error(e)

            // TODO handle exception
            throw e
        }
    }
}
