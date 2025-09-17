package `in`.dragonbra.javasteam.depotdownloader

/**
 * TODO
 */
@Suppress("ArrayInDataClass")
data class DepotDownloadInfo(
    val depotid: Int,
    val appId: Int,
    val manifestId: Long,
    val branch: String,
    val installDir: String,
    val depotKey: ByteArray,
)
