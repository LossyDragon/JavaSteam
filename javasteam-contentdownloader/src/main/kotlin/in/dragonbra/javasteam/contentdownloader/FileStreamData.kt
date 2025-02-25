package `in`.dragonbra.javasteam.contentdownloader

import kotlinx.coroutines.sync.Semaphore
import java.nio.channels.FileChannel

/**
 * @author Oxters
 * @author Lossy
 * @since 29-10-2024
 */
data class FileStreamData(
    var fileStream: FileChannel?,
    val fileLock: Semaphore,
    var chunksToDownload: Int,
)
