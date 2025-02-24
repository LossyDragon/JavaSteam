package `in`.dragonbra.javasteam.steam.contentdownloader

/**
 * @author Oxters
 * @author Lossy
 * @since 29-10-2024
 */
data class DepotDownloadCounter(
    var completeDownloadSize: Long = 0,
    var sizeDownloaded: Long = 0,
    var depotBytesCompressed: Long = 0,
    var depotBytesUncompressed: Long = 0,
)
