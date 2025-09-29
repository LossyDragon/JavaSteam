package `in`.dragonbra.javasteam.depotdownloader.data

import `in`.dragonbra.javasteam.depotdownloader.ContentDownloader

class UgcItem @JvmOverloads constructor(
    appId: Int,
    val ugcId: Long = ContentDownloader.INVALID_MANIFEST_ID,
    downloadManifestOnly: Boolean = false,
) : DownloadItem(appId, downloadManifestOnly)
