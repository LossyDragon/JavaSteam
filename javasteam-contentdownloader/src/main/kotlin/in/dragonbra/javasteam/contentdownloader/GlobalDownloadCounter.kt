package `in`.dragonbra.javasteam.contentdownloader

/**
 * @author Oxters
 * @author Lossy
 * @since 29-10-2024
 */
data class GlobalDownloadCounter(
    var completeDownloadSize: Long = 0,
    var totalBytesCompressed: Long = 0,
    var totalBytesUncompressed: Long = 0,
)
