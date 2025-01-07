package `in`.dragonbra.javasteam.steam.contentdownloader

import kotlinx.coroutines.sync.Semaphore
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicInteger

data class FileStreamData(
    var fileStream: FileChannel?,
    val fileLock: Semaphore,
    var chunksToDownload: AtomicInteger,
)
