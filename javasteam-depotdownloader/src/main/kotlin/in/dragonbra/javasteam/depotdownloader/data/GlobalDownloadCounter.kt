package `in`.dragonbra.javasteam.depotdownloader.data

data class GlobalDownloadCounter(
    var completeDownloadSize: Long = 0,
    var totalBytesCompressed: Long = 0,
    var totalBytesUncompressed: Long = 0,
)
