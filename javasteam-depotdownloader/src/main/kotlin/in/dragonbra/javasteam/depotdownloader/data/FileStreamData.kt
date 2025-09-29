package `in`.dragonbra.javasteam.depotdownloader.data

import kotlinx.coroutines.sync.Mutex
import okio.BufferedSink

data class FileStreamData(
    var fileStream: BufferedSink? = null,
    val fileLock: Mutex = Mutex(),
    var chunksToDownload: Int = 0,
)
