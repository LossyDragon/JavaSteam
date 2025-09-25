package `in`.dragonbra.javasteam.depotdownloader.data

import kotlinx.coroutines.sync.Mutex
import java.io.RandomAccessFile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

data class FileStreamData @ExperimentalAtomicApi constructor(
    var fileStream: RandomAccessFile? = null,
    val fileLock: Mutex = Mutex(),
    var chunksToDownload: AtomicInt = AtomicInt(0)
)
