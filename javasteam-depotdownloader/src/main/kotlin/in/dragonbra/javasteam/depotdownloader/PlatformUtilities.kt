package `in`.dragonbra.javasteam.depotdownloader

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

object PlatformUtilities {

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
            // TODO log 'e'
            File(path).also { file ->
                file.setExecutable(value)
            }
        } catch (e: IOException) {
            // TODO handle exception
            throw e
        }
    }
}
