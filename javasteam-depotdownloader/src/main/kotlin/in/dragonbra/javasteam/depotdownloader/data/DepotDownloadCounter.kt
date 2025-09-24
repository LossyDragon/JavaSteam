package `in`.dragonbra.javasteam.depotdownloader.data

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

data class DepotDownloadCounter @ExperimentalAtomicApi constructor(
    val completeDownloadSize: AtomicLong = AtomicLong(0L),
    val sizeDownloaded: AtomicLong = AtomicLong(0L),
    val depotBytesCompressed: AtomicLong = AtomicLong(0L),
    val depotBytesUncompressed: AtomicLong = AtomicLong(0L),
)
