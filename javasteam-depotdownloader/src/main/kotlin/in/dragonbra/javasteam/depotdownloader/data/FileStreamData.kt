package `in`.dragonbra.javasteam.depotdownloader.data

import kotlinx.coroutines.sync.Semaphore
import okio.BufferedSink

private data class FileStreamData(
    var sink: BufferedSink,
    val fileLock: Semaphore = Semaphore(1),
    var chunksToDownload: Int = 0
)
