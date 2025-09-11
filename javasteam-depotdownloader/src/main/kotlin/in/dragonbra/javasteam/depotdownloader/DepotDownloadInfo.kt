package `in`.dragonbra.javasteam.depotdownloader

/**
 * TODO
 */
@Suppress("ArrayInDataClass")
data class DepotDownloadInfo(
    val depotid: UInt,
    val appId: UInt,
    val manifestId: ULong,
    val branch: String,
    val installDir: String,
    val depotKey: ByteArray,
)
