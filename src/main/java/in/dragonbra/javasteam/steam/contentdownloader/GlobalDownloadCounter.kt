package `in`.dragonbra.javasteam.steam.contentdownloader

/**
 * @author Oxters
 * @author Lossy
 * @since 29-10-2024
 */
data class GlobalDownloadCounter(
    val completeDownloadSize: Long = 0,
    var totalBytesCompressed: Long = 0,
    var totalBytesUncompressed: Long = 0,
)
