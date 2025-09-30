package `in`.dragonbra.javasteam.depotdownloader.data

import kotlinx.coroutines.sync.Mutex
import java.io.Closeable
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger

data class FileStreamData(
    var fileStream: RandomAccessFile? = null,
    val fileLock: Mutex = Mutex(),
    var chunksToDownload: AtomicInteger = AtomicInteger(0),
) : Closeable {
    override fun close() {
        fileStream?.close()
    }
}
