package `in`.dragonbra.javasteam.depotdownloader.data

class AppItem @JvmOverloads constructor(
    appId: Int,
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
) : DownloadItem(appId, downloadManifestOnly)
