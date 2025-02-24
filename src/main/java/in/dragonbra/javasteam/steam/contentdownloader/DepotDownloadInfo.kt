package `in`.dragonbra.javasteam.steam.contentdownloader

/**
 * @author Oxters
 * @author Lossy
 * @since 29-10-2024
 */
@Suppress("ArrayInDataClass")
data class DepotDownloadInfo(
    val depotId: Int,
    val appId: Int,
    val manifestId: Long,
    val branch: String,
    val installDir: String,
    val depotKey: ByteArray?,
)
