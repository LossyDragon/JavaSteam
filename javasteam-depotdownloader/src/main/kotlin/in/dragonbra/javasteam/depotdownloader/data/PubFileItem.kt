package `in`.dragonbra.javasteam.depotdownloader.data

import `in`.dragonbra.javasteam.depotdownloader.ContentDownloader

class PubFileItem @JvmOverloads constructor(
    appId: Int,
    val pubfile: Long = ContentDownloader.INVALID_MANIFEST_ID,
    downloadManifestOnly: Boolean = false,
) : DownloadItem(appId, downloadManifestOnly)
