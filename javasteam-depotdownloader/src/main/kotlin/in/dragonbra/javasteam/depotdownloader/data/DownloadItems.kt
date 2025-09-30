package `in`.dragonbra.javasteam.depotdownloader.data

import `in`.dragonbra.javasteam.depotdownloader.ContentDownloader

// https://kotlinlang.org/docs/coding-conventions.html#source-file-organization

abstract class DownloadItem(
    val appId: Int,
    val installDirectory: String,
    val downloadManifestOnly: Boolean,
)

class UgcItem @JvmOverloads constructor(
    appId: Int,
    installDirectory: String,
    val ugcId: Long = ContentDownloader.INVALID_MANIFEST_ID,
    downloadManifestOnly: Boolean = false,
) : DownloadItem(appId, installDirectory, downloadManifestOnly)

class PubFileItem @JvmOverloads constructor(
    appId: Int,
    installDirectory: String,
    val pubfile: Long = ContentDownloader.INVALID_MANIFEST_ID,
    downloadManifestOnly: Boolean = false,
) : DownloadItem(appId, installDirectory, downloadManifestOnly)

class AppItem @JvmOverloads constructor(
    appId: Int,
    installDirectory: String,
    val branch: String? = null,
    val branchPassword: String? = null,
    val downloadAllPlatforms: Boolean = false,
    val os: String? = null,
    val downloadAllArchs: Boolean = false,
    val osArch: String? = null,
    val downloadAllLanguages: Boolean = false,
    val language: String? = null,
    val lowViolence: Boolean = false,
    val depot: List<Int> = emptyList(),
    val manifest: List<Long> = emptyList(),
    downloadManifestOnly: Boolean = false,
) : DownloadItem(appId, installDirectory, downloadManifestOnly)
