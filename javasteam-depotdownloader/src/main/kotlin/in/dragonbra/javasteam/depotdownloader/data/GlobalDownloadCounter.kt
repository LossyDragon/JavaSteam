package `in`.dragonbra.javasteam.depotdownloader.data

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

data class GlobalDownloadCounter @ExperimentalAtomicApi constructor(
    val completeDownloadSize: AtomicLong = AtomicLong(0L),
    val totalBytesCompressed: AtomicLong = AtomicLong(0L),
    val totalBytesUncompressed: AtomicLong = AtomicLong(0L),
)
