package `in`.dragonbra.javasteam.depotdownloader.data

import okio.Path

data class DepotDownloadInfo(
    val depotId: Int,
    val appId: Int,
    val manifestId: Long,
    val branch: String,
    val installDir: Path,
    val depotKey: ByteArray,
)
