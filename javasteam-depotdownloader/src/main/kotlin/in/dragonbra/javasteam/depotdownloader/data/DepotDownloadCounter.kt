package `in`.dragonbra.javasteam.depotdownloader.data

data class DepotDownloadCounter(
    var completeDownloadSize: Long = 0,
    var sizeDownloaded: Long = 0,
    var depotBytesCompressed: Long = 0,
    var depotBytesUncompressed: Long = 0,
)
